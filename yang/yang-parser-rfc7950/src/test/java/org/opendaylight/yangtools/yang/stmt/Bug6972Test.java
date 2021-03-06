/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yangtools.yang.stmt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.LeafEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.UnitsEffectiveStatement;

public class Bug6972Test {

    @Ignore
    @Test
    public void allUnitsShouldBeTheSameInstance() throws Exception {
        final SchemaContext schemaContext = StmtTestUtils.parseYangSources("/bugs/bug6972");
        assertNotNull(schemaContext);
        assertEquals(3, schemaContext.getModules().size());

        final Revision revision = Revision.of("2016-10-20");
        final Module foo = schemaContext.findModule("foo", revision).get();
        final Module bar = schemaContext.findModule("bar", revision).get();
        final Module baz = schemaContext.findModule("baz", revision).get();

        final QName barExportCont = QName.create("bar-ns", "bar-export", revision);
        final QName barFooCont = QName.create("bar-ns", "bar-foo", revision);
        final QName barFooLeaf = QName.create("bar-ns", "foo", revision);

        final UnitsEffectiveStatement unitsBar1 = getEffectiveUnits(bar, barExportCont, barFooLeaf);
        final UnitsEffectiveStatement unitsBar2 = getEffectiveUnits(bar, barFooCont, barFooLeaf);

        final QName bazExportCont = QName.create("baz-ns", "baz-export", revision);
        final QName bazFooCont = QName.create("baz-ns", "baz-foo", revision);
        final QName bazFooLeaf = QName.create("baz-ns", "foo", revision);

        final UnitsEffectiveStatement unitsBaz1 = getEffectiveUnits(baz, bazExportCont, bazFooLeaf);
        final UnitsEffectiveStatement unitsBaz2 = getEffectiveUnits(baz, bazFooCont, bazFooLeaf);

        assertTrue(unitsBar1 == unitsBar2 && unitsBar1 == unitsBaz1 && unitsBar1 == unitsBaz2);
    }

    private static UnitsEffectiveStatement getEffectiveUnits(final Module module, final QName containerQName,
            final QName leafQName) {
        UnitsEffectiveStatement units = null;

        final ContainerSchemaNode cont = (ContainerSchemaNode) module.getDataChildByName(containerQName);
        assertNotNull(cont);
        final LeafSchemaNode leaf = (LeafSchemaNode) cont.getDataChildByName(leafQName);
        assertNotNull(leaf);

        for (EffectiveStatement<?, ?> effStmt : ((LeafEffectiveStatement) leaf).effectiveSubstatements()) {
            if (effStmt instanceof UnitsEffectiveStatement) {
                units = (UnitsEffectiveStatement) effStmt;
                break;
            }
        }

        return units;
    }
}