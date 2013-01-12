package com.remi.pompes;

import java.util.ArrayList;

public class Module {
	private ArrayList<Semaine> semaines = new ArrayList<Semaine>();
	private int start;
	private String detail;

	public final Semaine getSemaine(int index) {
		return semaines.get(index);
	}

	public void addSemaine(Semaine semaine) {
		semaines.add(semaine);
	}

	public void setStart(int start) {
		this.start = start;
	}
	
	public void setDetail(String detail) {
		this.detail = detail;
	}

	public int getStart() {
		return start;
	}

	public String getDetail() {
		return detail;
	}

	public int size() {
		return semaines.size();
	}
}