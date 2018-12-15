package org.ab.uae;

import java.io.File;
import java.util.ArrayList;
import org.ab.nativelayer.ImportFileView;
import android.app.Application;
import xtvapps.prg.amiga.uae4droid.R;

public class FloppyImportView extends ImportFileView {

	public FloppyImportView() {
		super(new String [] { "adf" } );
		virtualDir = false;
	}

	private static final long serialVersionUID = -8756086087950123786L;

	@Override
	public ArrayList<String> getExtendedList(Application application, File file) {
		return null;
	}

	@Override
	public String getExtra2(int position) {
		return null;
	}

	@Override
	public int getIcon(int position) {
		return R.drawable.file;
	}

}
