package org.openjdk.jol.layouters;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openjdk.jol.datamodel.DataModel;
import org.openjdk.jol.datamodel.Model32;
import org.openjdk.jol.datamodel.Model64;
import org.openjdk.jol.datamodel.ModelVM;
import org.openjdk.jol.info.ClassData;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.util.ClassGenerator;

import java.util.Random;

public class LayouterInvariantsHotspotOldTest {

    private static final DataModel[] MODELS = {
            new ModelVM(),
            new Model32(),
            new Model64(false, false, 8),
            new Model64(false, true, 8),
            new Model64(true, true, 8),
    };

    private static final int ITERATIONS = 3000;

    private static Class<?>[] CLS;
    private static int[] SEEDS;

    @BeforeClass
    public static void buildClasses() throws Exception {
        Random seeder = new Random();
        CLS = new Class<?>[ITERATIONS];
        SEEDS = new int[ITERATIONS];
        for (int c = 0; c < ITERATIONS; c++) {
            SEEDS[c] = seeder.nextInt();
            CLS[c] = ClassGenerator.generate(new Random(SEEDS[c]), 5, 50);
        }
    }

    @Test
    public void testHotspot_Old() {
        for (int c = 0; c < ITERATIONS; c++) {
            ClassData cd = ClassData.parseClass(CLS[c]);
            try {
                for (DataModel model : MODELS) {
                    HotSpotLayouter layouter = new HotSpotLayouter(model, 8);
                    layouter.layout(cd);
                }
            } catch (Exception e) {
                Assert.fail("Failed. Seed = " + SEEDS[c]);
            }
        }
    }

}
