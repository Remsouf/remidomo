package com.remi.pompes;

import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class WelcomeActivity extends FragmentActivity implements ActionBar.TabListener, TestDialogFragment.TestDialogListener {

	private static final String TAG = WelcomeActivity.class.getSimpleName();

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    
    private ArrayList<Module> dataPompes = new ArrayList<Module>();
    private ArrayList<Module> dataTractions = new ArrayList<Module>();
    private ArrayList<Module> dataAbdos = new ArrayList<Module>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        // Create the adapter that will return a fragment for each of the three primary sections
        // of the app.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the action bar.
        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding tab.
        // We can also use ActionBar.Tab#select() to do this if we have a reference to the
        // Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
                ExerciceFragment fragment = (ExerciceFragment) mSectionsPagerAdapter.getItem(position);
                fragment.updateView();
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by the adapter.
            // Also specify this Activity object, which implements the TabListener interface, as the
            // listener for when this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }

        dataPompes = parseDataFile("pompes.xml");
        dataTractions = parseDataFile("tractions.xml");
        dataAbdos = parseDataFile("abdos.xml");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_welcome, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	int currentIndex = mViewPager.getCurrentItem();
    	final ExerciceFragment fragment = (ExerciceFragment) mSectionsPagerAdapter.getItem(currentIndex);

    	switch (item.getItemId()) {
        	case R.id.menu_test:
        		currentIndex = mViewPager.getCurrentItem();
        		DialogFragment dialog = new TestDialogFragment(fragment);
				dialog.show(getFragmentManager(), "TestDialogFragment");
        		return true;
        	case R.id.menu_reset:
        		AlertDialog.Builder builder = new AlertDialog.Builder(this);
        		builder.setMessage(String.format(getString(R.string.reset_dialog), fragment.getName()))
        		.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
        			public void onClick(DialogInterface dialog, int id) {
        				ExerciceFragment fragment = (ExerciceFragment) mSectionsPagerAdapter.getItem(mViewPager.getCurrentItem());
        				fragment.resetCurrentProgress();
        			}
        		})
        		.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        			public void onClick(DialogInterface dialog, int id) {
        				// User cancelled the dialog
        				// Do nothing
        			}
        		});
        		// Create the AlertDialog object and return it
        		AlertDialog alert = builder.create();
        		alert.show();
        		return true;
        	default:
        		return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to one of the primary
     * sections of the app.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

    	private Fragment pompesFragment;
    	private Fragment tractionsFragment;
    	private Fragment abdosFragment;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);

            pompesFragment = new ExerciceFragment();
            Bundle args = new Bundle();
            args.putString("index", ExerciceFragment.TabIndex.POMPES.toString());
            pompesFragment.setArguments(args);
            
            tractionsFragment = new ExerciceFragment();
            args = new Bundle();
            args.putString("index", ExerciceFragment.TabIndex.TRACTIONS.toString());
            tractionsFragment.setArguments(args);

            abdosFragment = new ExerciceFragment();
            args = new Bundle();
            args.putString("index", ExerciceFragment.TabIndex.ABDOS.toString());
            abdosFragment.setArguments(args);
        }

        @Override
        public Fragment getItem(int i) {
            if (i == 0) {
            	return pompesFragment;
            } else if (i == 1) {
            	return tractionsFragment;
            } else if (i == 2) {
            	return abdosFragment;
            }
            return null;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0: return getString(R.string.title_pompes).toUpperCase();
                case 1: return getString(R.string.title_tractions).toUpperCase();
                case 2: return getString(R.string.title_abdos).toUpperCase();
            }
            return null;
        }
    }

    public final ArrayList<Module> getData(ExerciceFragment.TabIndex index) {
    	if (index == ExerciceFragment.TabIndex.POMPES) {
    		return dataPompes;
    	} else if (index == ExerciceFragment.TabIndex.TRACTIONS) {
    		return dataTractions;
    	} else if (index == ExerciceFragment.TabIndex.ABDOS) {
    		return dataAbdos;
    	} else {
    		assert(false);
    	}
    	return null;
    }

    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onDialogPositiveClick(DialogFragment dialog, int value, ExerciceFragment fragment) {
    	// Find first module to match score

    	final ArrayList<Module> data = getData(fragment.getIndex());
    	for (int i=0; i < data.size(); i++) {
    		Module module = data.get(i);
    		if (value <= module.getStart()) {
    			fragment.initCurrentProgress(Math.max(0, i-1));
    			return;
    		}
    	}
    	fragment.initCurrentProgress(data.size()-1);
    }

    private ArrayList<Module> parseDataFile(String fileName) {
    	ArrayList<Module> programme = new ArrayList<Module>();

    	AssetManager assetMgr = getAssets();
    	InputStream stream = null;
    	try {
    		stream = assetMgr.open(fileName);
    		InputSource is = new InputSource();
    		is.setByteStream(stream);

    		DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    		Document doc = db.parse(is);

    		// XML root
    		Node rootXml = doc.getElementsByTagName("programme").item(0);

    		// Traverse modules
    		NodeList modulesXml = rootXml.getChildNodes();
    		for (int i=0; i < modulesXml.getLength(); i++) {
    			Node moduleXml = modulesXml.item(i);
    			if (moduleXml.getNodeType() != Node.ELEMENT_NODE) {
    				continue;
    			}

    			Module module = new Module();
    			NamedNodeMap attrs = moduleXml.getAttributes();
    			module.setStart(Integer.parseInt(attrs.getNamedItem("start").getNodeValue()));

    			if (attrs.getNamedItem("detail") != null) {
    				module.setDetail(attrs.getNamedItem("detail").getNodeValue());
    			}

    			// Traverse semaines
    			NodeList semainesXml = moduleXml.getChildNodes();
    			for (int j=0; j < semainesXml.getLength(); j++) {
    				Node semaineXml = semainesXml.item(j);
    				if (semaineXml.getNodeType() != Node.ELEMENT_NODE) {
        				continue;
        			}

    				Semaine semaine = new Semaine();

    				// Traverse seances
    				NodeList seancesXml = semaineXml.getChildNodes();
    				for (int k=0; k < seancesXml.getLength(); k++) {
    					Node seanceXml = seancesXml.item(k);
    					if (seanceXml.getNodeType() != Node.ELEMENT_NODE) {
            				continue;
            			}

    					Seance seance = new Seance();
    					seance.setTempsRepos(Integer.parseInt(seanceXml.getAttributes().getNamedItem("repos").getNodeValue()));

    					// Traverse series
    					NodeList seriesXml = seanceXml.getChildNodes();
        				for (int l=0; l < seriesXml.getLength(); l++) {
        					Node serieXml = seriesXml.item(l);
        					if (serieXml.getNodeType() != Node.ELEMENT_NODE) {
                				continue;
                			}
        					
        					seance.addSerie(serieXml.getTextContent());
        				} // end series loop

    					semaine.addSeance(seance);
    				} // end seances loop

    				module.addSemaine(semaine);
    			} // end semaines loop
        		
    			programme.add(module);

    		} // end modules loop
    		
    		Log.d(TAG, "Succes parsing " + fileName);
    	} catch (java.io.IOException e) {
    		Log.e(TAG, "Erreur lecture assets dans " + fileName + " : " + e);
    		
    	} catch (ParserConfigurationException e) {
			Log.e(TAG, "Erreur config parser: " + e);
		} catch (SAXException e) {
			Log.e(TAG, "Erreur SAX: " + e);
		} finally {
    		if (stream != null) {
    			try {
    				stream.close();
    			} catch (java.io.IOException ignored) {}
    		}
    	}
    	
    	return programme;
    }
}
