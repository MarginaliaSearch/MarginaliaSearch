package nu.marginalia.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

class EasyLSHTest {

    @Test
    public void testEZLSH() {
        String sA = """
                In computer science, locality-sensitive hashing (LSH) is an algorithmic technique that hashes similar input items 
                into the same "buckets" with high probability.[1] (The number of buckets is much smaller than the universe of possible
                input items.)[1] Since similar items end up in the same buckets, this technique can be used for data clustering and
                nearest neighbor search. It differs from conventional hashing techniques in that hash collisions are maximized, not minimized.
                Alternatively, the technique can be seen as a way to reduce the dimensionality of high-dimensional data; high-dimensional input
                items can be reduced to low-dimensional versions while preserving relative distances between items.;
                """;

        String sB = """
                In computer science, locality-sensitive hashing (LSH) is an algorithmic technique that hashes similar input items 
                into the same "buckets" with high probability.[1] (The number of buckets is much smaller than the universe of possible
                input items.)[1] Since similar items end up in the same buckets, this technique can be used for data clustering and
                nearest neighbor search.
                
                 The wrath sing, goddess, of Peleus' son, Achilles, that destructive wrath which brought countless woes upon the Achaeans, 
                 and sent forth to Hades many valiant souls of heroes, and made them themselves spoil for dogs and every bird; thus the plan
                  of Zeus came to fulfillment, [5] from the time when1 first they parted in strife Atreus' son, king of men, and brilliant Achilles.
                  Who then of the gods was it that brought these two together to contend?
                 
                It differs from conventional hashing techniques in that hash collisions are maximized, not minimized.
                Alternatively, the technique can be seen as a way to reduce the dimensionality of high-dimensional data; high-dimensional input
                items can be reduced to low-dimensional versions while preserving relative distances between items.;
                """;

        String sC = """
                 The wrath sing, goddess, of Peleus' son, Achilles, that destructive wrath which brought countless woes upon the Achaeans, 
                 and sent forth to Hades many valiant souls of heroes, and made them themselves spoil for dogs and every bird; thus the plan
                  of Zeus came to fulfillment, [5] from the time when1 first they parted in strife Atreus' son, king of men, and brilliant Achilles.
                  Who then of the gods was it that brought these two together to contend?
                """;

        String sD = """
                Quo usque tandem abutere, Catilina, patientia nostra? quam diu etiam furor iste tuus nos eludet? quem ad finem sese effrenata iactabit 
                audacia? Nihilne te nocturnum praesidium Palati, nihil urbis vigiliae, nihil timor populi, nihil concursus bonorum omnium, nihil hic 
                munitissimus habendi senatus locus, nihil horum ora voltusque moverunt? Patere tua consilia non sentis, constrictam iam horum omnium 
                scientia teneri coniurationem tuam non vides? Quid proxima, quid superiore nocte egeris, ubi fueris, quos convocaveris, quid consilii 
                ceperis, quem nostrum ignorare arbitraris? [2] O tempora, o mores! Senatus haec intellegit. Consul videt; hic tamen vivit. Vivit? immo 
                vero etiam in senatum venit, fit publici consilii particeps, notat et designat oculis ad caedem unum quemque nostrum. Nos autem fortes 
                viri satis facere rei publicae videmur, si istius furorem ac tela vitemus. Ad mortem te, Catilina, duci iussu consulis iam pridem oportebat,
                 in te conferri pestem, quam tu in nos [omnes iam diu] machinaris.
                """;

        EasyLSH hashA = new EasyLSH();
        Arrays.stream(sA.split("\\s")).forEach(hashA::addOrdered);
        EasyLSH hashB = new EasyLSH();
        Arrays.stream(sB.split("\\s")).forEach(hashB::addOrdered);
        EasyLSH hashC = new EasyLSH();
        Arrays.stream(sC.split("\\s")).forEach(hashC::addOrdered);

        EasyLSH hashD = new EasyLSH();
        Arrays.stream(sD.split("\\s")).forEach(hashD::addOrdered);

        System.out.println(Long.toBinaryString(hashA.get()));
        System.out.println(Long.toBinaryString(hashB.get()));
        System.out.println(Long.toBinaryString(hashC.get()));
        System.out.println(Long.toBinaryString(hashD.get()));

        System.out.println(EasyLSH.hammingDistance(hashA, hashB));
        System.out.println(EasyLSH.hammingDistance(hashB, hashC));
        System.out.println(EasyLSH.hammingDistance(hashA, hashC));

        System.out.println(EasyLSH.hammingDistance(hashA, hashD));
        System.out.println(EasyLSH.hammingDistance(hashB, hashD));
        System.out.println(EasyLSH.hammingDistance(hashC, hashD));
    }
}