package com.remi.pompes;

import java.util.ArrayList;

public class Seance {
	private ArrayList<String> series = new ArrayList<String>();
	private int tempsRepos;

	public String getSerie(int index) {
		return series.get(index);
	}

	public void addSerie(String serie) {
		series.add(serie);
	}

	public void setTempsRepos(int temps) {
		tempsRepos = temps;
	}

	public int getTempsRepos() {
		return tempsRepos;
	}

	public int size() {
		return series.size();
	}
}