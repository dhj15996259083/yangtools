/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective;

import java.util.Collection;
import java.util.Map;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.Rfc6020Mapping;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.meta.IdentifierNamespace;
import org.opendaylight.yangtools.yang.model.api.meta.StatementDefinition;
import org.opendaylight.yangtools.yang.model.api.meta.StatementSource;
import org.opendaylight.yangtools.yang.model.api.stmt.TypeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.TypeStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.TypedefEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.TypedefStatement;
import org.opendaylight.yangtools.yang.model.util.type.DerivedTypeBuilder;
import org.opendaylight.yangtools.yang.model.util.type.DerivedTypes;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TypeDefEffectiveStatementImpl extends AbstractEffectiveSchemaNode<TypedefStatement> implements
        TypedefEffectiveStatement {
    private static final Logger LOG = LoggerFactory.getLogger(TypeDefEffectiveStatementImpl.class);
    private final TypeDefinition<?> typeDefinition;
    private TypeEffectiveStatement<TypeStatement> typeStatement;

    public TypeDefEffectiveStatementImpl(final StmtContext<QName, TypedefStatement, ?> ctx) {
        super(ctx);

        final TypeEffectiveStatement<?> typeEffectiveStmt = firstSubstatementOfType(TypeEffectiveStatement.class);
        final DerivedTypeBuilder<?> builder = DerivedTypes.derivedTypeBuilder(typeEffectiveStmt.getTypeDefinition(),
            ctx.getSchemaPath().get());
        for (EffectiveStatement<?, ?> stmt : effectiveSubstatements()) {
            if (stmt instanceof DefaultEffectiveStatementImpl) {
                builder.setDefaultValue(stmt.argument());
            } else if (stmt instanceof DescriptionEffectiveStatementImpl) {
                builder.setDescription(((DescriptionEffectiveStatementImpl)stmt).argument());
            } else if (stmt instanceof ReferenceEffectiveStatementImpl) {
                builder.setReference(((ReferenceEffectiveStatementImpl)stmt).argument());
            } else if (stmt instanceof StatusEffectiveStatementImpl) {
                builder.setStatus(((StatusEffectiveStatementImpl)stmt).argument());
            } else if (stmt instanceof UnitsEffectiveStatementImpl) {
                builder.setUnits(((UnitsEffectiveStatementImpl)stmt).argument());
            } else if (stmt instanceof UnknownEffectiveStatementImpl) {
                // FIXME: should not directly implement, I think
                builder.addUnknownSchemaNode((UnknownEffectiveStatementImpl)stmt);
            } else {
                if (!(stmt instanceof TypeEffectiveStatement)) {
                    LOG.debug("Ignoring statement {}", stmt);
                }
            }
        }

        typeDefinition = builder.build();
    }

    @Override
    public TypeDefinition<?> getTypeDefinition() {
        return typeDefinition;
    }

    public TypeEffectiveStatement<TypeStatement> asTypeEffectiveStatement() {
        TypeEffectiveStatement<TypeStatement> ret = typeStatement;
        if (ret == null) {
            synchronized (this) {
                ret = typeStatement;
                if (ret == null) {
                    ret = new ProxyTypeEffectiveStatement();
                    typeStatement = ret;
                }
            }
        }

        return ret;
    }

    private final class ProxyTypeEffectiveStatement implements TypeEffectiveStatement<TypeStatement> {
        @Override
        public TypeStatement getDeclared() {
            return null;
        }

        @Override
        public <K, V, N extends IdentifierNamespace<K, V>> V get(final Class<N> namespace, final K identifier) {
            return TypeDefEffectiveStatementImpl.this.get(namespace, identifier);
        }

        @Override
        public <K, V, N extends IdentifierNamespace<K, V>> Map<K, V> getAll(final Class<N> namespace) {
            return TypeDefEffectiveStatementImpl.this.getAll(namespace);
        }

        @Override
        public Collection<? extends EffectiveStatement<?, ?>> effectiveSubstatements() {
            // FIXME: this is tricky
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public StatementDefinition statementDefinition() {
            return Rfc6020Mapping.TYPE;
        }

        @Override
        public String argument() {
            return getQName().getLocalName();
        }

        @Override
        public StatementSource getStatementSource() {
            return StatementSource.CONTEXT;
        }

        @Override
        public TypeDefinition<?> getTypeDefinition() {
            return TypeDefEffectiveStatementImpl.this.getTypeDefinition();
        }
    }
}