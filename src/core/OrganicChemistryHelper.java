package core;

import java.util.*;
import java.util.stream.Collectors;

public class OrganicChemistryHelper {

    // (Internal classes CarbonNode and Substituent are unchanged)
    private static class CarbonNode {
        int id;
        List<CarbonNode> neighbors = new ArrayList<>();
        int distance;
        CarbonNode parentForPath;
        CarbonNode(int id) { this.id = id; }
        void addBond(CarbonNode neighbor) {
            if (!this.neighbors.contains(neighbor)) this.neighbors.add(neighbor);
            if (!neighbor.neighbors.contains(this)) neighbor.neighbors.add(this);
        }
        @Override public String toString() { return "Carbon-" + id; }
    }
    private record Substituent(int position, String name) implements Comparable<Substituent> {
        @Override
        public int compareTo(Substituent other) { return Integer.compare(this.position, other.position); }
    }

    // (Data maps are unchanged)
    private static final Map<Integer, String> IUPAC_PREFIXES = Map.ofEntries(Map.entry(1,"Meth"), Map.entry(2,"Eth"), Map.entry(3,"Prop"), Map.entry(4,"But"), Map.entry(5,"Pent"), Map.entry(6,"Hex"), Map.entry(7,"Hept"), Map.entry(8,"Oct"), Map.entry(9,"Non"), Map.entry(10,"Dec"));
    private static final Map<Integer, String> COUNT_PREFIXES = Map.ofEntries(Map.entry(2,"di"), Map.entry(3,"tri"), Map.entry(4,"tetra"));


    /** --- The Top-Level IUPAC Naming Method --- */
    public static String getIupacName(String formula) {
        List<CarbonNode> moleculeGraph = parseToGraph(formula);
        if (moleculeGraph.isEmpty()) return "Could not parse formula into a structure.";

        List<CarbonNode> parentChain = findLongestChainPath(moleculeGraph);
        if (parentChain.isEmpty()) return "Invalid structure";
        String parentName = IUPAC_PREFIXES.getOrDefault(parentChain.size(), "Unsupported") + "ane";

        List<Substituent> forwardSubs = findSubstituents(parentChain);
        List<CarbonNode> reversedParentChain = new ArrayList<>(parentChain);
        Collections.reverse(reversedParentChain);
        List<Substituent> backwardSubs = findSubstituents(reversedParentChain);

        List<Substituent> finalSubstituents = compareSubstituentLists(forwardSubs, backwardSubs);

        if (finalSubstituents.isEmpty()) {
            return parentName;
        } else {
            return assembleName(finalSubstituents, parentName);
        }
    }

    /** --- NEW, MORE ROBUST PARSER --- */
    private static List<CarbonNode> parseToGraph(String formula) {
        List<CarbonNode> allNodes = new ArrayList<>();
        if (formula == null || formula.isEmpty()) return allNodes;

        int idCounter = 0;
        CarbonNode lastCarbon = null; // Tracks the tip of the current chain being built
        Stack<CarbonNode> parentBranchPoints = new Stack<>();

        for (int i = 0; i < formula.length(); i++) {
            char c = formula.charAt(i);

            if (c == 'C') {
                idCounter++;
                CarbonNode currentCarbon = new CarbonNode(idCounter);
                allNodes.add(currentCarbon);

                if (lastCarbon != null) {
                    // If we are starting a branch, connect to the parent on the stack.
                    // Otherwise, connect to the last carbon in the current chain.
                    if (!parentBranchPoints.isEmpty() && lastCarbon == parentBranchPoints.peek()) {
                        currentCarbon.addBond(parentBranchPoints.peek());
                    } else {
                        currentCarbon.addBond(lastCarbon);
                    }
                }
                lastCarbon = currentCarbon;
            } else if (c == '(') {
                // The last carbon we saw is where the branch starts.
                if (lastCarbon != null) {
                    parentBranchPoints.push(lastCarbon);
                }
            } else if (c == ')') {
                // We are done with a branch. The next carbon should connect to the branch point.
                if (!parentBranchPoints.isEmpty()) {
                    lastCarbon = parentBranchPoints.pop();
                }
                // Check for multipliers like ")2"
                if (i + 1 < formula.length() && Character.isDigit(formula.charAt(i+1))) {
                    // This is a simplified multiplier handler for cases like C(CH3)2
                    // A more advanced version would be needed for C(CH2CH3)2
                    int multiplier = Character.getNumericValue(formula.charAt(i + 1));
                    CarbonNode parent = lastCarbon;
                    CarbonNode branchExample = allNodes.get(allNodes.size() - 1); // get the last added branch
                    for (int j = 1; j < multiplier; j++) {
                        idCounter++;
                        CarbonNode newBranch = new CarbonNode(idCounter);
                        allNodes.add(newBranch);
                        newBranch.addBond(parent);
                    }
                    i++; // Skip the number character
                }
            }
            // We ignore 'H' and digits that are part of a CH group for structural parsing.
        }
        return allNodes;
    }

    /** Finds the longest chain path in a molecule graph. */
    private static List<CarbonNode> findLongestChainPath(List<CarbonNode> molecule) {
        if (molecule == null || molecule.isEmpty()) return new ArrayList<>();
        CarbonNode arbitraryStartNode = molecule.get(0);
        CarbonNode farthestNode = bfs(arbitraryStartNode, molecule);
        CarbonNode endOfLongestChain = bfs(farthestNode, molecule);
        List<CarbonNode> path = new ArrayList<>();
        CarbonNode currentNode = endOfLongestChain;
        while (currentNode != null) {
            path.add(currentNode);
            currentNode = currentNode.parentForPath;
        }
        Collections.reverse(path);
        return path;
    }

    /** Performs a Breadth-First Search (BFS), returning the farthest node found. */
    private static CarbonNode bfs(CarbonNode startNode, List<CarbonNode> molecule) {
        for (CarbonNode node : molecule) {
            node.distance = -1;
            node.parentForPath = null;
        }
        Queue<CarbonNode> queue = new LinkedList<>();
        startNode.distance = 0;
        queue.add(startNode);
        CarbonNode lastVisited = startNode;
        while (!queue.isEmpty()) {
            CarbonNode current = queue.poll();
            lastVisited = current;
            for (CarbonNode neighbor : current.neighbors) {
                if (neighbor.distance == -1) {
                    neighbor.distance = current.distance + 1;
                    neighbor.parentForPath = current;
                    queue.add(neighbor);
                }
            }
        }
        return lastVisited;
    }

    /** Iterates through the parent chain to find and name all attached branches. */
    private static List<Substituent> findSubstituents(List<CarbonNode> parentChain) {
        List<Substituent> substituents = new ArrayList<>();
        Set<CarbonNode> parentChainSet = new HashSet<>(parentChain);
        for (int i = 0; i < parentChain.size(); i++) {
            CarbonNode chainNode = parentChain.get(i);
            int position = i + 1;
            for (CarbonNode neighbor : chainNode.neighbors) {
                if (!parentChainSet.contains(neighbor)) {
                    int branchSize = countBranchSize(neighbor, chainNode);
                    String branchName = IUPAC_PREFIXES.getOrDefault(branchSize, "complex") + "yl";
                    substituents.add(new Substituent(position, branchName));
                }
            }
        }
        return substituents;
    }

    /** Helper to count the number of carbons in a branch. */
    private static int countBranchSize(CarbonNode branchRoot, CarbonNode parentToIgnore) {
        Stack<CarbonNode> stack = new Stack<>();
        Set<CarbonNode> visited = new HashSet<>();
        stack.push(branchRoot);
        visited.add(branchRoot);
        visited.add(parentToIgnore);
        int count = 0;
        while (!stack.isEmpty()) {
            CarbonNode current = stack.pop();
            count++;
            for (CarbonNode neighbor : current.neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    stack.push(neighbor);
                }
            }
        }
        return count;
    }

    /** Compares two lists of substituents to find the IUPAC-preferred one. */
    private static List<Substituent> compareSubstituentLists(List<Substituent> listA, List<Substituent> listB) {
        Collections.sort(listA);
        Collections.sort(listB);
        for (int i = 0; i < Math.min(listA.size(), listB.size()); i++) {
            if (listA.get(i).position() < listB.get(i).position()) return listA;
            if (listB.get(i).position() < listA.get(i).position()) return listB;
        }
        return listA; // Default to forward numbering if identical
    }

    /** Assembles the substituent prefixes and the parent name into a final IUPAC name. */
    private static String assembleName(List<Substituent> substituents, String parentName) {
        Map<String, List<Integer>> groupedSubstituents = new TreeMap<>();
        for (Substituent sub : substituents) {
            groupedSubstituents.computeIfAbsent(sub.name(), k -> new ArrayList<>()).add(sub.position());
        }
        StringBuilder prefixBuilder = new StringBuilder();
        for (Map.Entry<String, List<Integer>> entry : groupedSubstituents.entrySet()) {
            String name = entry.getKey().replace("yl", ""); // use methyl, not methylyl
            List<Integer> positions = entry.getValue();
            if (prefixBuilder.length() > 0) prefixBuilder.append("-");
            prefixBuilder.append(positions.stream().map(String::valueOf).collect(Collectors.joining(",")));
            prefixBuilder.append("-");
            prefixBuilder.append(COUNT_PREFIXES.getOrDefault(positions.size(), ""));
            prefixBuilder.append(name.toLowerCase());
        }
        return prefixBuilder.toString() + "yl" + parentName; // e.g. 2-methyl + butane
    }

    // The rest of the methods are unchanged and provided for completeness.
    public static void startCLI(Scanner sc) { System.out.println("\n=== Organic Chemistry Helper ==="); System.out.println("Enter a semi-structural formula (e.g., CH3CH(CH3)CH3). Type 'exit' to return."); while (true) { System.out.print("\nFormula: "); String formula = sc.nextLine().trim(); if (formula.equalsIgnoreCase("exit")) break; if (formula.isEmpty()) continue; analyzeMolecule(formula); } }
    public static void analyzeMolecule(String formula) { System.out.println("\n--- Analysis for " + formula + " ---"); try { Map<String, Integer> elementCounts = EquationBalancer.parseCompound(formula); if (elementCounts.isEmpty()) { System.out.println("❌ Could not parse the formula."); return; } String molecularFormula = formatMapToFormula(elementCounts); System.out.println("✅ Molecular Formula: " + molecularFormula); double molarMass = MolarMassCalculator.computeMass(elementCounts, false); System.out.printf("✅ Molar Mass: %.3f g/mol%n", molarMass); System.out.println("✅ Percentage Composition:"); PercentageComposition.calculate(molecularFormula); String functionalGroup = identifyFunctionalGroup(formula); System.out.println("✅ Functional Group: " + functionalGroup); String iupacName = getIupacName(formula); System.out.println("✅ IUPAC Name: " + iupacName); } catch (Exception e) { System.out.println("❌ Could not analyze formula: " + e.getMessage()); e.printStackTrace(); } }
    private static String formatMapToFormula(Map<String, Integer> elementCounts) { List<String> elements = new ArrayList<>(elementCounts.keySet()); elements.sort((a, b) -> { if (a.equals("C")) return -1; if (b.equals("C")) return 1; if (a.equals("H") && !a.equals(b)) return -1; if (b.equals("H") && !a.equals(b)) return 1; return a.compareTo(b); }); StringBuilder sb = new StringBuilder(); for (String element : elements) { sb.append(element); int count = elementCounts.get(element); if (count > 1) { sb.append(count); } } return sb.toString(); }
    public static String identifyFunctionalGroup(String formula) { if (formula.endsWith("COOH")) return "Carboxylic Acid"; if (formula.contains("COO")) return "Ester"; if (formula.contains("CONH")) return "Amide"; if (formula.endsWith("CHO")) return "Aldehyde"; if (formula.contains("CO")) return "Ketone"; if (formula.contains("OH")) return "Alcohol"; if (formula.contains("NH") || (formula.contains("N") && !formula.contains("NO"))) return "Amine"; if (formula.contains("O")) return "Ether"; try { Map<String, Integer> elements = EquationBalancer.parseCompound(formula); if (elements.containsKey("C") && elements.containsKey("H") && elements.size() == 2) { int n = elements.get("C"); int m = elements.get("H"); if (m == 2 * n + 2) return "Alkane"; if (m == 2 * n) return "Alkene"; if (m == 2 * n - 2) return "Alkyne"; return "Cyclic or Aromatic Hydrocarbon"; } } catch (Exception e) { /* ignore */ } return "Unknown or Inorganic Compound"; }
}
