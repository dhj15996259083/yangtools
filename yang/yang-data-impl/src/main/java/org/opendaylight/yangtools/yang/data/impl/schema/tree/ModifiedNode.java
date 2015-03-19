/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.impl.schema.tree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ModificationType;
import org.opendaylight.yangtools.yang.data.api.schema.tree.StoreTreeNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.spi.TreeNode;

/**
 * Node Modification Node and Tree
 *
 * Tree which structurally resembles data tree and captures client modifications
 * to the data store tree.
 *
 * This tree is lazily created and populated via {@link #modifyChild(PathArgument)}
 * and {@link TreeNode} which represents original state as tracked by {@link #getOriginal()}.
 */
@NotThreadSafe
final class ModifiedNode extends NodeModification implements StoreTreeNode<ModifiedNode> {
    static final Predicate<ModifiedNode> IS_TERMINAL_PREDICATE = new Predicate<ModifiedNode>() {
        @Override
        public boolean apply(final @Nonnull ModifiedNode input) {
            Preconditions.checkNotNull(input);
            switch (input.getOperation()) {
            case DELETE:
            case MERGE:
            case WRITE:
                return true;
            case TOUCH:
            case NONE:
                return false;
            }

            throw new IllegalArgumentException(String.format("Unhandled modification type %s", input.getOperation()));
        }
    };

    private final Map<PathArgument, ModifiedNode> children;
    private final Optional<TreeNode> original;
    private final PathArgument identifier;
    private LogicalOperation operation = LogicalOperation.NONE;
    private Optional<TreeNode> snapshotCache;
    private NormalizedNode<?, ?> value;

    private ModifiedNode(final PathArgument identifier, final Optional<TreeNode> original, final boolean isOrdered) {
        this.identifier = identifier;
        this.original = original;

        if (isOrdered) {
            children = new LinkedHashMap<>();
        } else {
            children = new HashMap<>();
        }
    }

    /**
     * Return the value which was written to this node.
     *
     * @return Currently-written value
     */
    public NormalizedNode<?, ?> getWrittenValue() {
        return value;
    }

    @Override
    public PathArgument getIdentifier() {
        return identifier;
    }

    @Override
    Optional<TreeNode> getOriginal() {
        return original;
    }


    @Override
    LogicalOperation getOperation() {
        return operation;
    }

    /**
     *
     * Returns child modification if child was modified
     *
     * @return Child modification if direct child or it's subtree
     *  was modified.
     *
     */
    @Override
    public Optional<ModifiedNode> getChild(final PathArgument child) {
        return Optional.<ModifiedNode> fromNullable(children.get(child));
    }

    /**
     *
     * Returns child modification if child was modified, creates {@link ModifiedNode}
     * for child otherwise.
     *
     * If this node's {@link ModificationType} is {@link ModificationType#UNMODIFIED}
     * changes modification type to {@link ModificationType#SUBTREE_MODIFIED}
     *
     * @param child
     * @return {@link ModifiedNode} for specified child, with {@link #getOriginal()}
     *         containing child metadata if child was present in original data.
     */
    ModifiedNode modifyChild(final PathArgument child, final boolean isOrdered) {
        clearSnapshot();
        if (operation == LogicalOperation.NONE) {
            updateModificationType(LogicalOperation.TOUCH);
        }
        final ModifiedNode potential = children.get(child);
        if (potential != null) {
            return potential;
        }

        final Optional<TreeNode> currentMetadata;
        if (original.isPresent()) {
            final TreeNode orig = original.get();
            currentMetadata = orig.getChild(child);
        } else {
            currentMetadata = Optional.absent();
        }

        final ModifiedNode newlyCreated = new ModifiedNode(child, currentMetadata, isOrdered);
        children.put(child, newlyCreated);
        return newlyCreated;
    }

    /**
     * Returns all recorded direct child modification
     *
     * @return all recorded direct child modifications
     */
    @Override
    Collection<ModifiedNode> getChildren() {
        return children.values();
    }

    /**
     * Records a delete for associated node.
     */
    void delete() {
        final LogicalOperation newType;

        switch (operation) {
        case DELETE:
        case NONE:
            // We need to record this delete.
            newType = LogicalOperation.DELETE;
            break;
        case MERGE:
        case TOUCH:
        case WRITE:
            /*
             * We are canceling a previous modification. This is a bit tricky,
             * as the original write may have just introduced the data, or it
             * may have modified it.
             *
             * As documented in BUG-2470, a delete of data introduced in this
             * transaction needs to be turned into a no-op.
             */
            newType = original.isPresent() ? LogicalOperation.DELETE : LogicalOperation.NONE;
            break;
        default:
            throw new IllegalStateException("Unhandled deletion of node with " + operation);
        }

        clearSnapshot();
        children.clear();
        this.value = null;
        updateModificationType(newType);
    }

    /**
     * Records a write for associated node.
     *
     * @param value
     */
    void write(final NormalizedNode<?, ?> value) {
        clearSnapshot();
        updateModificationType(LogicalOperation.WRITE);
        children.clear();
        this.value = value;
    }

    void merge(final NormalizedNode<?, ?> value) {
        clearSnapshot();
        updateModificationType(LogicalOperation.MERGE);

        /*
         * Blind overwrite of any previous data is okay, no matter whether the node
         * is simple or complex type.
         *
         * If this is a simple or complex type with unkeyed children, this merge will
         * be turned into a write operation, overwriting whatever was there before.
         *
         * If this is a container with keyed children, there are two possibilities:
         * - if it existed before, this value will never be consulted and the children
         *   will get explicitly merged onto the original data.
         * - if it did not exist before, this value will be used as a seed write and
         *   children will be merged into it.
         * In either case we rely on OperationWithModification to manipulate the children
         * before calling this method, so unlike a write we do not want to clear them.
         */
        this.value = value;
    }

    /**
     * Seal the modification node and prune any children which has not been
     * modified.
     */
    void seal() {
        clearSnapshot();

        // Walk all child nodes and remove any children which have not
        // been modified.
        final Iterator<ModifiedNode> it = children.values().iterator();
        while (it.hasNext()) {
            final ModifiedNode child = it.next();
            child.seal();

            if (child.operation == LogicalOperation.NONE) {
                it.remove();
            }
        }

        // A TOUCH node without any children is a no-op
        if (operation == LogicalOperation.TOUCH && children.isEmpty()) {
            updateModificationType(LogicalOperation.NONE);
        }
    }

    private void clearSnapshot() {
        snapshotCache = null;
    }

    Optional<TreeNode> getSnapshot() {
        return snapshotCache;
    }

    Optional<TreeNode> setSnapshot(final Optional<TreeNode> snapshot) {
        snapshotCache = Preconditions.checkNotNull(snapshot);
        return snapshot;
    }

    private void updateModificationType(final LogicalOperation type) {
        operation = type;
        clearSnapshot();
    }

    @Override
    public String toString() {
        return "NodeModification [identifier=" + identifier + ", modificationType="
                + operation + ", childModification=" + children + "]";
    }

    /**
     * Create a node which will reflect the state of this node, except it will behave as newly-written
     * value. This is useful only for merge validation.
     *
     * @param value Value associated with the node
     * @return An isolated node. This node should never reach a datatree.
     */
    ModifiedNode asNewlyWritten(final NormalizedNode<?, ?> value) {
        final ModifiedNode ret = new ModifiedNode(getIdentifier(), Optional.<TreeNode>absent(), false);
        ret.write(value);
        return ret;
    }

    public static ModifiedNode createUnmodified(final TreeNode metadataTree, final boolean isOrdered) {
        return new ModifiedNode(metadataTree.getIdentifier(), Optional.of(metadataTree), isOrdered);
    }
}
