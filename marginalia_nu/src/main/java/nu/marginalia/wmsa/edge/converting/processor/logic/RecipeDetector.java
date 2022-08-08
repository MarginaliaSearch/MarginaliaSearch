package nu.marginalia.wmsa.edge.converting.processor.logic;

import ca.rmen.porterstemmer.PorterStemmer;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.sqrt;

public class RecipeDetector {
    private static final int AVG_RECIPE_LENGTH = 250;

    private final Map<String, Double> termValues = new HashMap<>();

    public RecipeDetector() {
        PorterStemmer ps = new PorterStemmer();

        // these terms appear in most recipes
        termValues.put(ps.stemWord("ingredients"), 0.3);
        termValues.put(ps.stemWord("recipe"), 0.1);
        termValues.put(ps.stemWord("preparations"), 0.1);
        termValues.put(ps.stemWord("instructions"), 0.1);

        // penalize restaurant menus
        termValues.put(ps.stemWord("menu"), -0.5);

        // error non habet ius
        termValues.put(ps.stemWord("email"), -0.15);
        termValues.put(ps.stemWord("checkout"), -0.15);
        termValues.put(ps.stemWord("reviews"), -0.15);
        termValues.put(ps.stemWord("newsletter"), -0.15);

        // measures
        termValues.put(ps.stemWord("dl"), 0.05);
        termValues.put(ps.stemWord("l"), 0.05);
        termValues.put(ps.stemWord("g"), 0.05);
        termValues.put(ps.stemWord("ml"), 0.05);
        termValues.put(ps.stemWord("tsp"), 0.05);
        termValues.put(ps.stemWord("teaspoons"), 0.05);
        termValues.put(ps.stemWord("tbsp"), 0.05);
        termValues.put(ps.stemWord("tablespoons"), 0.05);
        termValues.put(ps.stemWord("cups"), 0.05);
        termValues.put(ps.stemWord("quarts"), 0.05);
        termValues.put(ps.stemWord("pints"), 0.05);

        // techniques
        termValues.put(ps.stemWord("grate"), 0.05);
        termValues.put(ps.stemWord("cut"), 0.05);
        termValues.put(ps.stemWord("peel"), 0.05);
        termValues.put(ps.stemWord("chop"), 0.05);
        termValues.put(ps.stemWord("slice"), 0.05);
        termValues.put(ps.stemWord("debone"), 0.05);
        termValues.put(ps.stemWord("julienne"), 0.05);
        termValues.put(ps.stemWord("saute"), 0.05);
        termValues.put(ps.stemWord("fry"), 0.05);
        termValues.put(ps.stemWord("boil"), 0.05);
        termValues.put(ps.stemWord("parboil"), 0.05);
        termValues.put(ps.stemWord("roast"), 0.05);
        termValues.put(ps.stemWord("grill"), 0.05);
        termValues.put(ps.stemWord("sear"), 0.05);
        termValues.put(ps.stemWord("heat"), 0.05);
        termValues.put(ps.stemWord("dice"), 0.05);
        termValues.put(ps.stemWord("bake"), 0.05);
        termValues.put(ps.stemWord("strain"), 0.05);
        termValues.put(ps.stemWord("melt"), 0.05);
        termValues.put(ps.stemWord("garnish"), 0.05);
        termValues.put(ps.stemWord("preheat"), 0.05);
        termValues.put(ps.stemWord("sprinkle"), 0.05);
        termValues.put(ps.stemWord("spritz"), 0.05);

        // utensils
        termValues.put(ps.stemWord("colander"), 0.05);
        termValues.put(ps.stemWord("pot"), 0.05);
        termValues.put(ps.stemWord("pan"), 0.05);
        termValues.put(ps.stemWord("oven"), 0.05);
        termValues.put(ps.stemWord("stove"), 0.05);
        termValues.put(ps.stemWord("skillet"), 0.05);
        termValues.put(ps.stemWord("wok"), 0.05);
        termValues.put(ps.stemWord("knife"), 0.05);
        termValues.put(ps.stemWord("grater"), 0.05);

        // baking
        termValues.put(ps.stemWord("yeast"), 0.025);
        termValues.put(ps.stemWord("sourdough"), 0.025);
        termValues.put(ps.stemWord("flour"), 0.025);
        termValues.put(ps.stemWord("sugar"), 0.025);
        termValues.put(ps.stemWord("rye"), 0.025);
        termValues.put(ps.stemWord("wheat"), 0.025);
        termValues.put(ps.stemWord("dough"), 0.025);
        termValues.put(ps.stemWord("rise"), 0.025);

        // vegetables
        termValues.put(ps.stemWord("lettuce"), 0.025);
        termValues.put(ps.stemWord("onions"), 0.025);
        termValues.put(ps.stemWord("parsnips"), 0.025);
        termValues.put(ps.stemWord("beets"), 0.025);
        termValues.put(ps.stemWord("carrots"), 0.025);
        termValues.put(ps.stemWord("chilies"), 0.025);
        termValues.put(ps.stemWord("peppers"), 0.025);
        termValues.put(ps.stemWord("chives"), 0.025);
        termValues.put(ps.stemWord("tomatoes"), 0.025);
        termValues.put(ps.stemWord("salad"), 0.025);
        termValues.put(ps.stemWord("leeks"), 0.025);
        termValues.put(ps.stemWord("shallots"), 0.025);
        termValues.put(ps.stemWord("avocado"), 0.025);
        termValues.put(ps.stemWord("asparagus"), 0.025);
        termValues.put(ps.stemWord("cucumbers"), 0.025);
        termValues.put(ps.stemWord("eggplants"), 0.025);
        termValues.put(ps.stemWord("broccoli"), 0.025);
        termValues.put(ps.stemWord("kale"), 0.05);

        termValues.put(ps.stemWord("jalapeno"), 0.025);
        termValues.put(ps.stemWord("habanero"), 0.025);

        termValues.put(ps.stemWord("mushrooms"), 0.025);
        termValues.put(ps.stemWord("shiitake"), 0.025);
        termValues.put(ps.stemWord("chanterelles"), 0.025);

        // brotein
        termValues.put(ps.stemWord("meat"), 0.025);
        termValues.put(ps.stemWord("beef"), 0.025);
        termValues.put(ps.stemWord("chicken"), 0.025);
        termValues.put(ps.stemWord("turkey"), 0.025);
        termValues.put(ps.stemWord("cheese"), 0.025);
        termValues.put(ps.stemWord("pork"), 0.025);
        termValues.put(ps.stemWord("tofu"), 0.025);
        termValues.put(ps.stemWord("salmon"), 0.025);
        termValues.put(ps.stemWord("cod"), 0.025);
        termValues.put(ps.stemWord("veal"), 0.025);
        termValues.put(ps.stemWord("eggs"), 0.025);
        termValues.put(ps.stemWord("lentils"), 0.025);
        termValues.put(ps.stemWord("chickpeas"), 0.025);

        // carbs
        termValues.put(ps.stemWord("rice"), 0.025);
        termValues.put(ps.stemWord("noodles"), 0.025);
        termValues.put(ps.stemWord("beans"), 0.025);
        termValues.put(ps.stemWord("ramen"), 0.025);

        // japan
        termValues.put(ps.stemWord("miso"), 0.025);
        termValues.put(ps.stemWord("natto"), 0.025);
        termValues.put(ps.stemWord("udon"), 0.025);
        termValues.put(ps.stemWord("soba"), 0.025);
        termValues.put(ps.stemWord("shichimi"), 0.025);
        termValues.put(ps.stemWord("nori"), 0.025);

        // korea
        termValues.put(ps.stemWord("kimchi"), 0.025);

        // fat of the land
        termValues.put(ps.stemWord("salt"), 0.025);
        termValues.put(ps.stemWord("oil"), 0.025);
        termValues.put(ps.stemWord("olive"), 0.025);
        termValues.put(ps.stemWord("feta"), 0.025);
        termValues.put(ps.stemWord("parmesan"), 0.025);
        termValues.put(ps.stemWord("mozzarella"), 0.025);
        termValues.put(ps.stemWord("gouda"), 0.025);
        termValues.put(ps.stemWord("cheese"), 0.025);
        termValues.put(ps.stemWord("mayonnaise"), 0.025);
        termValues.put(ps.stemWord("butter"), 0.025);

        // spices and sauces
        termValues.put(ps.stemWord("pepper"), 0.025);
        termValues.put(ps.stemWord("garlic"), 0.025);
        termValues.put(ps.stemWord("sriracha"), 0.025);
        termValues.put(ps.stemWord("sambal"), 0.025);
        termValues.put(ps.stemWord("soy"), 0.025);
        termValues.put(ps.stemWord("cumin"), 0.025);
        termValues.put(ps.stemWord("thyme"), 0.025);
        termValues.put(ps.stemWord("basil"), 0.025);
        termValues.put(ps.stemWord("oregano"), 0.025);
        termValues.put(ps.stemWord("cilantro"), 0.025);
        termValues.put(ps.stemWord("ginger"), 0.025);
        termValues.put(ps.stemWord("curry"), 0.025);

        termValues.put(ps.stemWord("water"), 0.025);

        // dessert
        termValues.put(ps.stemWord("lemons"), 0.025);
        termValues.put(ps.stemWord("melons"), 0.025);
        termValues.put(ps.stemWord("cherries"), 0.025);
        termValues.put(ps.stemWord("apples"), 0.025);
        termValues.put(ps.stemWord("pears"), 0.025);

        termValues.put(ps.stemWord("chocolate"), 0.025);
        termValues.put(ps.stemWord("vanilla"), 0.025);

        // dairy
        termValues.put(ps.stemWord("milk"), 0.025);
        termValues.put(ps.stemWord("creamer"), 0.025);
        termValues.put(ps.stemWord("quark"), 0.025);
        termValues.put(ps.stemWord("cream"), 0.025);


        // dishes
        termValues.put(ps.stemWord("cake"), 0.025);
        termValues.put(ps.stemWord("pie"), 0.025);
        termValues.put(ps.stemWord("crust"), 0.025);
        termValues.put(ps.stemWord("bread"), 0.025);
        termValues.put(ps.stemWord("omelet"), 0.025);
        termValues.put(ps.stemWord("soup"), 0.025);

    }

    public double recipeP(DocumentLanguageData dld) {

        Map<String, Double> values = new HashMap<>();
        int count = 0;
        for (var sentence : dld.sentences) {

            for (var word : sentence) {
                count++;

                final String stemmed = word.stemmed();
                final Double value = termValues.get(stemmed);

                if (value != null) {
                    values.put(stemmed, value);
                }
            }

        }

        if (count == 0) return 0.;

        double lengthPenalty = sqrt(AVG_RECIPE_LENGTH)/sqrt(max(AVG_RECIPE_LENGTH, count));

        return values.values().stream().mapToDouble(Double::valueOf).sum() * lengthPenalty;
    }

}
