package com.remi.pompes;

import java.util.ArrayList;

public class Semaine {
	private ArrayList<Seance> seances = new ArrayList<Seance>();

	public final Seance getSeance(int index) {
		return seances.get(index);
	}

	public void addSeance(Seance seance) {
		seances.add(seance);
	}

	public int size() {
		return seances.size();
	}
}