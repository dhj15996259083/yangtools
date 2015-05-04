/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.api.schema.tree;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import java.util.Iterator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class holding methods useful when dealing with {@link DataTreeCandidate} instances.
 */
@Beta
public final class DataTreeCandidates {
    private static final Logger LOG = LoggerFactory.getLogger(DataTreeCandidates.class);
    private DataTreeCandidates() {
        throw new UnsupportedOperationException();
    }

    public static DataTreeCandidate newDataTreeCandidate(final YangInstanceIdentifier rootPath, final DataTreeCandidateNode rootNode) {
        return new DefaultDataTreeCandidate(rootPath, rootNode);
    }

    public static DataTreeCandidate fromNormalizedNode(final YangInstanceIdentifier rootPath, final NormalizedNode<?, ?> node) {
        return new DefaultDataTreeCandidate(rootPath, new NormalizedNodeDataTreeCandidateNode(node));
    }

    public static void applyToCursor(final DataTreeModificationCursor cursor, final DataTreeCandidate candidate) {
        DataTreeCandidateNodes.applyToCursor(cursor, candidate.getRootNode());
    }

    public static void applyToModification(final DataTreeModification modification, final DataTreeCandidate candidate) {
        if (modification instanceof CursorAwareDataTreeModification) {
            try (DataTreeModificationCursor cursor = ((CursorAwareDataTreeModification) modification).createCursor(candidate.getRootPath())) {
                applyToCursor(cursor, candidate);
            }
            return;
        }

        final DataTreeCandidateNode node = candidate.getRootNode();
        final YangInstanceIdentifier path = candidate.getRootPath();
        switch (node.getModificationType()) {
        case DELETE:
            modification.delete(path);
            LOG.debug("Modification {} deleted path {}", modification, path);
            break;
        case SUBTREE_MODIFIED:
            LOG.debug("Modification {} modified path {}", modification, path);

            NodeIterator iterator = new NodeIterator(null, path, node.getChildNodes().iterator());
            do {
                iterator = iterator.next(modification);
            } while (iterator != null);
            break;
        case UNMODIFIED:
            LOG.debug("Modification {} unmodified path {}", modification, path);
            // No-op
            break;
        case WRITE:
            modification.write(path, node.getDataAfter().get());
            LOG.debug("Modification {} written path {}", modification, path);
            break;
        default:
            throw new IllegalArgumentException("Unsupported modification " + node.getModificationType());
        }
    }

    private static final class NodeIterator {
        private final Iterator<DataTreeCandidateNode> iterator;
        private final YangInstanceIdentifier path;
        private final NodeIterator parent;

        public NodeIterator(@Nullable final NodeIterator parent, @Nonnull final YangInstanceIdentifier path,
                @Nonnull final Iterator<DataTreeCandidateNode> iterator) {
            this.iterator = Preconditions.checkNotNull(iterator);
            this.path = Preconditions.checkNotNull(path);
            this.parent = parent;
        }

        NodeIterator next(final DataTreeModification modification) {
            while (iterator.hasNext()) {
                final DataTreeCandidateNode node = iterator.next();
                final YangInstanceIdentifier child = path.node(node.getIdentifier());

                switch (node.getModificationType()) {
                case DELETE:
                    modification.delete(child);
                    LOG.debug("Modification {} deleted path {}", modification, child);
                    break;
                case SUBTREE_MODIFIED:
                    LOG.debug("Modification {} modified path {}", modification, child);
                    return new NodeIterator(this, child, node.getChildNodes().iterator());
                case UNMODIFIED:
                    LOG.debug("Modification {} unmodified path {}", modification, child);
                    // No-op
                    break;
                case WRITE:
                    modification.write(child, node.getDataAfter().get());
                    LOG.debug("Modification {} written path {}", modification, child);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported modification " + node.getModificationType());
                }
            }

            return parent;
        }
    }
}
