package nu.marginalia;

import nu.marginalia.util.AndCardIntSet;
import org.openjdk.jmh.annotations.*;
import org.roaringbitmap.RoaringBitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BitSetTest {
    @org.openjdk.jmh.annotations.State(Scope.Benchmark)
    public static class State {
        List<RoaringBitmap> roar = new ArrayList<>();
        List<AndCardIntSet> acbs = new ArrayList<>();

        List<RoaringBitmap> roarLow = new ArrayList<>();
        List<RoaringBitmap> roarHigh = new ArrayList<>();

        List<AndCardIntSet> acbsLow = new ArrayList<>();
        List<AndCardIntSet> acbsHigh = new ArrayList<>();

        @Setup(Level.Trial)
        public void setUp() {
            var rand = new Random();

            for (int i = 0; i < 100; i++) {
                int card = 1 + rand.nextInt(10);

                var rb = new RoaringBitmap();
                var cbs = new AndCardIntSet();

                for (int j = 0; j < card; j++) {
                    int val = rand.nextInt(1_000_000);
                    rb.add(val);
                    cbs.add(val);
                }
                acbsLow.add(cbs);
                roarLow.add(rb);
            }

            for (int i = 0; i < 10; i++) {
                int card = 1 + rand.nextInt(10000, 20000);

                var rb = new RoaringBitmap();

                for (int j = 0; j < card; j++) {
                    int val = rand.nextInt(1_000_000);
                    rb.add(val);
                }
                acbsHigh.add(AndCardIntSet.of(rb));
                roarHigh.add(rb);
            }



            for (int i = 0; i < 100000; i++) {
                var rb = new RoaringBitmap();
                var cbs = new AndCardIntSet();

                int val = rand.nextInt(1_000_000);
                rb.add(val);
                cbs.add(val);

                acbs.add(cbs);
                roar.add(rb);
            }

            for (int i = 0; i < 10000; i++) {
                int card = 1 + rand.nextInt(10);

                var rb = new RoaringBitmap();
                var cbs = new AndCardIntSet();

                for (int j = 0; j < card; j++) {
                    int val = rand.nextInt(1_000_000);
                    rb.add(val);
                    cbs.add(val);
                }
                acbs.add(cbs);
                roar.add(rb);
            }
            for (int i = 0; i < 1000; i++) {
                int card = 1 + rand.nextInt(100);

                var rb = new RoaringBitmap();
                var cbs = new AndCardIntSet();

                for (int j = 0; j < card; j++) {
                    int val = rand.nextInt(1_000_000);
                    rb.add(val);
                    cbs.add(val);
                }
                acbs.add(cbs);
                roar.add(rb);
            }
            for (int i = 0; i < 100; i++) {
                int card = 1 + rand.nextInt(1000);

                var rb = new RoaringBitmap();
                var cbs = new AndCardIntSet();

                for (int j = 0; j < card; j++) {
                    int val = rand.nextInt(1_000_000);
                    rb.add(val);
                    cbs.add(val);
                }
                acbs.add(cbs);
                roar.add(rb);
            }
            for (int i = 0; i < 100; i++) {
                int card = 1 + rand.nextInt(10000);

                var rb = new RoaringBitmap();
                var cbs = new AndCardIntSet();

                for (int j = 0; j < card; j++) {
                    int val = rand.nextInt(1_000_000);
                    rb.add(val);
                    cbs.add(val);
                }
                acbs.add(cbs);
                roar.add(rb);
            }

            for (int i = 0; i < 2; i++) {
                int card = 1 + rand.nextInt(100000);

                var rb = new RoaringBitmap();
                var cbs = new AndCardIntSet();

                for (int j = 0; j < card; j++) {
                    int val = rand.nextInt(1_000_000);
                    rb.add(val);
                    cbs.add(val);
                }
                acbs.add(cbs);
                roar.add(rb);
            }
            Collections.shuffle(acbs);
            Collections.shuffle(roar);
        }
    }

//
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @Fork(value = 5, warmups = 5)
//    public Object roaringCard(State state) {
//        long val = 0;
//
//        for (int i = 0; i < state.roar.size(); i++) {
//            for (int j = i+1; j < state.roar.size(); j++) {
//                val += RoaringBitmap.andCardinality(state.roar.get(i), state.roar.get(j));
//            }
//        }
//
//        return val;
//    }
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @Fork(value = 2, warmups = 2)
//    public Object roaringCardNorm(State state) {
//        long val = 0;
//
//        for (int i = 0; i < state.roar.size()/1000; i++) {
//            for (int j = i+1; j < state.roar.size(); j++) {
//
//                var a = state.roar.get(i);
//                var b = state.roar.get(j);
//                val += RoaringBitmap.andCardinality(a, b) / (Math.sqrt(a.getCardinality()*b.getCardinality()));
//            }
//        }
//
//        return val;
//    }
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @Fork(value = 5, warmups = 5)
//    public Object cbsCard(State state) {
//        long val = 0;
//
//        for (int i = 0; i < state.roar.size(); i++) {
//            for (int j = i+1; j < state.roar.size(); j++) {
//                val += AndCardIntSet.andCardinality(state.acbs.get(i), state.acbs.get(j));
//            }
//        }
//
//        return val;
//    }
//
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @Fork(value = 1, warmups = 1)
//    public Object cbsCardNorm(State state) {
//        double val = 0;
//
//        for (int i = 0; i < state.roar.size()/1000; i++) {
//            for (int j = i+1; j < state.roar.size(); j++) {
//                var a = state.acbs.get(i);
//                var b = state.acbs.get(j);
//                val += AndCardIntSet.andCardinality(a, b) / (Math.sqrt(a.cardinality()*b.cardinality()));
//            }
//        }
//
//        return val;
//    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Fork(value = 1, warmups = 1)
    public Object cbsLowLow(State state) {
        double val = 0;

        for (int i = 0; i < state.acbsLow.size(); i++) {
            for (int j = 0; j < state.acbsLow.size(); j++) {
                var a = state.acbsLow.get(i);
                var b = state.acbsLow.get(j);
                val += AndCardIntSet.andCardinality(a, b) / (Math.sqrt(a.getCardinality()*b.getCardinality()));
            }
        }

        return val;
    }


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Fork(value = 1, warmups = 1)
    public Object cbsHighHigh(State state) {
        double val = 0;

        for (int i = 0; i < state.acbsHigh.size(); i++) {
            for (int j = 0; j < state.acbsHigh.size(); j++) {
                var a = state.acbsHigh.get(i);
                var b = state.acbsHigh.get(j);
                val += AndCardIntSet.andCardinality(a, b) / (Math.sqrt(a.getCardinality()*b.getCardinality()));
            }
        }

        return val;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Fork(value = 1, warmups = 1)
    public Object cbsHighLow(State state) {
        double val = 0;

        for (int i = 0; i < state.acbsHigh.size(); i++) {
            for (int j = 0; j < state.acbsLow.size(); j++) {
                var a = state.acbsHigh.get(i);
                var b = state.acbsLow.get(j);
                val += AndCardIntSet.andCardinality(a, b) / (Math.sqrt(a.getCardinality()*b.getCardinality()));
            }
        }

        return val;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Fork(value = 1, warmups = 1)
    public Object roarLowLow(State state) {
        double val = 0;

        for (int i = 0; i < state.roarLow.size(); i++) {
            for (int j = 0; j < state.roarLow.size(); j++) {
                var a = state.roarLow.get(i);
                var b = state.roarLow.get(j);
                val += RoaringBitmap.andCardinality(a, b) / (Math.sqrt(a.getCardinality()*b.getCardinality()));
            }
        }

        return val;
    }


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Fork(value = 1, warmups = 1)
    public Object roarHighLow(State state) {
        double val = 0;

        for (int i = 0; i < state.roarHigh.size(); i++) {
            for (int j = 0; j < state.roarLow.size(); j++) {
                var a = state.roarHigh.get(i);
                var b = state.roarLow.get(j);
                val += RoaringBitmap.andCardinality(a, b) / (Math.sqrt(a.getCardinality()*b.getCardinality()));
            }
        }

        return val;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Fork(value = 1, warmups = 1)
    public Object roarHighHigh(State state) {
        double val = 0;

        for (int i = 0; i < state.roarHigh.size(); i++) {
            for (int j = 0; j < state.roarHigh.size(); j++) {
                var a = state.roarHigh.get(i);
                var b = state.roarHigh.get(j);
                val += RoaringBitmap.andCardinality(a, b) / (Math.sqrt(a.getCardinality()*b.getCardinality()));
            }
        }

        return val;
    }
}