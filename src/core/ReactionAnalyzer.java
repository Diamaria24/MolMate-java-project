package core;

import java.util.*;
import java.util.stream.Collectors;

public class ReactionAnalyzer {

    // --- Data for Oxidation State Rules ---
    private static final Set<String> GROUP_1_METALS = Set.of("Li", "Na", "K", "Rb", "Cs", "Fr");
    private static final Set<String> GROUP_2_METALS = Set.of("Be", "Mg", "Ca", "Sr", "Ba", "Ra");
    private static final Set<String> HALOGENS = Set.of("F", "Cl", "Br", "I");

    /**
     * Calculates the oxidation states of elements in a neutral compound.
     *
     * @param formula The chemical formula string (e.g., "H2SO4").
     * @return A map of each element to its oxidation state (e.g., {S=+6, O=-2, H=+1}).
     */
    public static Map<String, Integer> getOxidationStates(String formula) {
        Map<String, Integer> states = new HashMap<>();
        Map<String, Integer> counts = EquationBalancer.parseCompound(formula);
        if (counts.size() == 1) {
            states.put(counts.keySet().iterator().next(), 0);
            return states;
        }
        List<String> elements = new ArrayList<>(counts.keySet());
        int sumOfKnownStates = 0;
        if (counts.containsKey("F")) {
            states.put("F", -1);
            sumOfKnownStates += -1 * counts.get("F");
            elements.remove("F");
        }
        for (String element : new ArrayList<>(elements)) {
            if (GROUP_1_METALS.contains(element)) {
                states.put(element, 1);
                sumOfKnownStates += 1 * counts.get(element);
                elements.remove(element);
            } else if (GROUP_2_METALS.contains(element)) {
                states.put(element, 2);
                sumOfKnownStates += 2 * counts.get(element);
                elements.remove(element);
            }
        }
        if (counts.containsKey("H") && !states.containsKey("H")) {
            states.put("H", 1);
            sumOfKnownStates += 1 * counts.get("H");
            elements.remove("H");
        }
        if (counts.containsKey("O") && !states.containsKey("O")) {
            states.put("O", -2);
            sumOfKnownStates += -2 * counts.get("O");
            elements.remove("O");
        }
        for (String element : new ArrayList<>(elements)) {
            if (HALOGENS.contains(element)) {
                states.put(element, -1);
                sumOfKnownStates += -1 * counts.get(element);
                elements.remove(element);
            }
        }
        if (elements.size() == 1) {
            String unknownElement = elements.get(0);
            int stateOfUnknown = -sumOfKnownStates / counts.get(unknownElement);
            states.put(unknownElement, stateOfUnknown);
        } else if (elements.size() > 1) {
            return new HashMap<>();
        }
        return states;
    }

    /**
     * Analyzes a reaction for redox changes and identifies agents.
     * This new version correctly handles complex cases like disproportionation.
     *
     * @param balancedEquation The balanced equation string.
     * @return A formatted string detailing the redox analysis.
     */
    public static String analyzeRedox(String balancedEquation) {
        String[] sides = balancedEquation.split("->");
        String[] reactants = Arrays.stream(sides[0].trim().split("\\+")).map(s -> s.trim().replaceAll("^[0-9]+\\s*", "")).toArray(String[]::new);
        String[] products = Arrays.stream(sides[1].trim().split("\\+")).map(s -> s.trim().replaceAll("^[0-9]+\\s*", "")).toArray(String[]::new);

        Map<String, Map<String, Integer>> allStates = new HashMap<>();
        Set<String> allElements = new HashSet<>();

        // Pre-calculate oxidation states for all compounds
        Arrays.stream(reactants).forEach(c -> { allStates.put(c, getOxidationStates(c)); allElements.addAll(allStates.get(c).keySet()); });
        Arrays.stream(products).forEach(c -> { allStates.put(c, getOxidationStates(c)); allElements.addAll(allStates.get(c).keySet()); });

        StringBuilder changes = new StringBuilder();
        Set<String> oxidizedElements = new HashSet<>(), reducedElements = new HashSet<>();
        Set<String> reducingAgents = new HashSet<>(), oxidizingAgents = new HashSet<>();

        for (String element : allElements) {
            // Collect ALL states for an element on both sides
            Set<Integer> reactantStates = Arrays.stream(reactants)
                    .filter(c -> allStates.get(c).containsKey(element))
                    .map(c -> allStates.get(c).get(element))
                    .collect(Collectors.toSet());

            Set<Integer> productStates = Arrays.stream(products)
                    .filter(c -> allStates.get(c).containsKey(element))
                    .map(c -> allStates.get(c).get(element))
                    .collect(Collectors.toSet());

            // If the sets of states are not identical, a change has occurred.
            if (!reactantStates.equals(productStates) && !reactantStates.isEmpty() && !productStates.isEmpty()) {
                Integer oldState = reactantStates.iterator().next(); // Assuming element comes from one reactant
                String oldStateStr = oldState > 0 ? "+" + oldState : oldState.toString();
                String newStatesStr = productStates.stream()
                        .map(s -> s > 0 ? "+" + s : s.toString())
                        .collect(Collectors.joining(", "));
                changes.append(String.format("  - %s changes from %s to %s\n", element, oldStateStr, newStatesStr));

                // Check for oxidation (state increases)
                if (Collections.max(productStates) > Collections.max(reactantStates)) {
                    oxidizedElements.add(element);
                    Arrays.stream(reactants).filter(c -> allStates.get(c).containsKey(element)).forEach(reducingAgents::add);
                }
                // Check for reduction (state decreases)
                if (Collections.min(productStates) < Collections.min(reactantStates)) {
                    reducedElements.add(element);
                    Arrays.stream(reactants).filter(c -> allStates.get(c).containsKey(element)).forEach(oxidizingAgents::add);
                }
            }
        }

        if (changes.length() == 0) {
            return "This is NOT a redox reaction (no change in oxidation states).";
        } else {
            StringBuilder result = new StringBuilder("This IS a redox reaction.\n");
            result.append("State Changes:\n").append(changes);
            result.append("Analysis:\n");
            if (!oxidizedElements.isEmpty()) result.append(String.format("  - Oxidized Element(s): %s\n", oxidizedElements));
            if (!reducedElements.isEmpty()) result.append(String.format("  - Reduced Element(s): %s\n", reducedElements));
            if (!reducingAgents.isEmpty()) result.append(String.format("  - Reducing Agent(s): %s\n", reducingAgents));
            if (!oxidizingAgents.isEmpty()) result.append(String.format("  - Oxidizing Agent(s): %s\n", oxidizingAgents));
            return result.toString();
        }
    }

    // --- CLI updated to include new analysis ---
    public static void startCLI(Scanner sc) {
        System.out.println("\n=== Reaction Properties Analyzer ===");
        System.out.println("Enter a BALANCED chemical equation. Type 'exit' to return.");

        while(true) {
            System.out.print("\nEquation: ");
            String equation = sc.nextLine().trim();
            if (equation.equalsIgnoreCase("exit")) break;
            if (equation.isEmpty()) continue;

            System.out.println("\n--- Analysis ---");
            String type = classifyReaction(equation);
            System.out.println("✅ Reaction Type: " + type);

            System.out.println("\n✅ Redox Analysis:");
            String redoxInfo = analyzeRedox(equation);
            System.out.println(redoxInfo);
        }
    }

    // --- Unchanged methods from before ---
    private static boolean isElement(String formula) { Map<String, Integer> elements = EquationBalancer.parseCompound(formula); return elements.size() == 1; }
    public static String classifyReaction(String balancedEquation) {
        String[] sides = balancedEquation.split("->"); if (sides.length != 2) return "Invalid Equation Format";
        String[] reactants = Arrays.stream(sides[0].trim().split("\\+")).map(s -> s.trim().replaceAll("^[0-9]+\\s*", "")).toArray(String[]::new);
        String[] products = Arrays.stream(sides[1].trim().split("\\+")).map(s -> s.trim().replaceAll("^[0-9]+\\s*", "")).toArray(String[]::new);
        boolean hasO2Reactant = Arrays.asList(reactants).contains("O2");
        boolean hasCO2Product = Arrays.asList(products).contains("CO2");
        boolean hasH2OProduct = Arrays.asList(products).contains("H2O");
        boolean hasHydrocarbon = Arrays.stream(reactants).anyMatch(r -> r.contains("C") && r.contains("H"));
        if (hasO2Reactant && hasCO2Product && hasH2OProduct && products.length == 2 && hasHydrocarbon) return "Combustion";
        if (reactants.length > 1 && products.length == 1) return "Synthesis (Combination)";
        if (reactants.length == 1 && products.length > 1) return "Decomposition";
        if (reactants.length == 2 && products.length == 2) {
            boolean r1e = isElement(reactants[0]), r2e = isElement(reactants[1]), p1e = isElement(products[0]), p2e = isElement(products[1]);
            if ((r1e ^ r2e) && (p1e ^ p2e)) return "Single Displacement";
            if (!r1e && !r2e && !p1e && !p2e) return "Double Displacement";
        }
        return "Unknown / Complex Reaction";
    }
}
