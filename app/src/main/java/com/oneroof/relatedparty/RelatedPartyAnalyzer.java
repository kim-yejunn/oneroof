package com.oneroof.relatedparty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.oneroof.masterdata.IdentityKind;

/**
 * Applies the related-party definition from the data dictionary: agencies sharing two or more of
 * representative, address and settlement account are the same related party, and the relation is
 * transitive. Pure computation over the evidence it is given.
 */
public final class RelatedPartyAnalyzer {

	private RelatedPartyAnalyzer() {
	}

	public static RelatedPartyAnalysis analyze(LocalDate asOf, List<IdentityEvidence> evidence) {
		Map<String, List<IdentityEvidence>> byAgency = new TreeMap<>();
		for (IdentityEvidence item : evidence) {
			byAgency.computeIfAbsent(item.agencyId(), (key) -> new ArrayList<>()).add(item);
		}
		List<String> agencies = List.copyOf(byAgency.keySet());

		List<AgencyLink> links = new ArrayList<>();
		for (int i = 0; i < agencies.size(); i++) {
			for (int j = i + 1; j < agencies.size(); j++) {
				List<IdentifierMatch> matches = matches(byAgency.get(agencies.get(i)), byAgency.get(agencies.get(j)));
				if (!matches.isEmpty()) {
					links.add(new AgencyLink(agencies.get(i), agencies.get(j), matches));
				}
			}
		}

		Map<String, String> parent = new TreeMap<>();
		agencies.forEach((agency) -> parent.put(agency, agency));
		for (AgencyLink link : links) {
			if (link.isRelated()) {
				union(parent, link.agencyA(), link.agencyB());
			}
		}

		Map<String, List<String>> membersByRoot = new TreeMap<>();
		for (String agency : agencies) {
			membersByRoot.computeIfAbsent(find(parent, agency), (key) -> new ArrayList<>()).add(agency);
		}

		List<RelatedGroup> groups = new ArrayList<>();
		for (List<String> members : membersByRoot.values()) {
			if (members.size() < 2) {
				continue;
			}
			List<AgencyLink> internal = links.stream()
				.filter((link) -> members.contains(link.agencyA()) && members.contains(link.agencyB()))
				.toList();
			groups.add(new RelatedGroup("RPG-" + members.get(0), List.copyOf(members), internal));
		}
		groups.sort(Comparator.comparing(RelatedGroup::groupKey));

		List<AgencyLink> nearMisses = links.stream()
			.filter((link) -> !link.isRelated())
			.filter((link) -> !find(parent, link.agencyA()).equals(find(parent, link.agencyB())))
			.toList();

		return new RelatedPartyAnalysis(asOf, List.copyOf(groups), nearMisses);
	}

	/** At most one match per identifier kind, preferring evidence registered in the master on both sides. */
	private static List<IdentifierMatch> matches(List<IdentityEvidence> left, List<IdentityEvidence> right) {
		Map<IdentityKind, IdentifierMatch> best = new EnumMap<>(IdentityKind.class);
		for (IdentityEvidence a : left) {
			for (IdentityEvidence b : right) {
				if (a.kind() != b.kind() || !a.normalizedValue().equals(b.normalizedValue())) {
					continue;
				}
				IdentifierMatch candidate = new IdentifierMatch(a.kind(), a, b);
				IdentifierMatch current = best.get(a.kind());
				if (current == null || (!current.isRegisteredOnBothSides() && candidate.isRegisteredOnBothSides())) {
					best.put(a.kind(), candidate);
				}
			}
		}
		return List.copyOf(best.values());
	}

	private static String find(Map<String, String> parent, String agency) {
		String root = agency;
		while (!parent.get(root).equals(root)) {
			root = parent.get(root);
		}
		parent.put(agency, root);
		return root;
	}

	private static void union(Map<String, String> parent, String a, String b) {
		String rootA = find(parent, a);
		String rootB = find(parent, b);
		if (rootA.equals(rootB)) {
			return;
		}
		// Keep the smaller id as the root so the group key stays stable as members join.
		if (rootA.compareTo(rootB) < 0) {
			parent.put(rootB, rootA);
		}
		else {
			parent.put(rootA, rootB);
		}
	}
}
