/* ========================================================================
 * PlantUML : a free UML diagram generator
 * ========================================================================
 *
 * (C) Copyright 2009-2017, Arnaud Roques
 *
 * Project Info:  http://plantuml.com
 * 
 * If you like this project or if you find it useful, you can support us at:
 * 
 * http://plantuml.com/patreon (only 1$ per month!)
 * http://plantuml.com/paypal
 * 
 * This file is part of PlantUML.
 *
 * PlantUML is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PlantUML distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 *
 * Original Author:  Arnaud Roques
 * 
 *
 */
package net.sourceforge.plantuml.preproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.plantuml.CharSequence2;
import net.sourceforge.plantuml.DefinitionsContainer;
import net.sourceforge.plantuml.FileSystem;
import net.sourceforge.plantuml.Log;
import net.sourceforge.plantuml.command.regex.Matcher2;
import net.sourceforge.plantuml.command.regex.MyPattern;
import net.sourceforge.plantuml.command.regex.Pattern2;

public class SubPreprocessor implements ReadLine {

	private static final String ID = "[A-Za-z_][A-Za-z_0-9]*";

	private static final Pattern2 includeSubPattern = MyPattern.cmpile("^[%s]*!includesub[%s]+[%g]?([^%g]+)[%g]?$");

	private static final Pattern2 startsub = MyPattern.cmpile("^[%s]*!startsub[%s]+(" + ID + ")");
	private static final Pattern2 endsub = MyPattern.cmpile("^[%s]*!endsub[%s]*");

	private final ReadLine source;
	private final Defines defines;
	private final DefinitionsContainer definitionsContainer;
	private final String charset;
	private final Map<String, Sub> subs = new HashMap<String, Sub>();
	private final List<String> config;
	private Sub learningSub;
	private ReadLine includedSub;

	public SubPreprocessor(List<String> config, String charset, Defines defines,
			DefinitionsContainer definitionsContainer, ReadLine source) {
		this.config = config;
		this.source = source;
		this.charset = charset;
		this.defines = defines;
		this.definitionsContainer = definitionsContainer;
	}

	public CharSequence2 readLine() throws IOException {
		if (includedSub != null) {
			final CharSequence2 s = includedSub.readLine();
			if (s != null) {
				eventuallyLearn(s);
				return s;
			}
			includedSub = null;
		}

		final CharSequence2 s = source.readLine();
		if (s == null) {
			return null;
		}

		final Matcher2 m1 = includeSubPattern.matcher(s);
		if (m1.find()) {
			return manageIncludeSub(s, m1);
		}

		Matcher2 m = startsub.matcher(s);
		if (m.find()) {
			return manageStartsub(m);
		}

		m = endsub.matcher(s);
		if (m.find()) {
			return manageEndsub(m);
		}
		eventuallyLearn(s);
		return s;
	}

	private void eventuallyLearn(final CharSequence2 s) {
		if (learningSub != null) {
			learningSub.add(s);
		}
	}

	private CharSequence2 manageIncludeSub(CharSequence2 s, Matcher2 m) throws IOException {
		final String name = m.group(1);
		final int idx = name.indexOf('!');
		if (idx != -1) {
			final String filename = name.substring(0, idx);
			final String blocname = name.substring(idx + 1);
			final File f = FileSystem.getInstance().getFile(PreprocessorInclude.withEnvironmentVariable(filename));
			if (f.exists() == false || f.isDirectory()) {
				return s.withErrorPreprocessor("Cannot include " + f.getAbsolutePath());
			}
			final Preprocessor data = new Preprocessor(config, getReaderInclude(s, f), charset, defines, null,
					definitionsContainer);
			while (data.readLine() != null) {
				// Read file
			}
			data.close();
			this.includedSub = data.getSub(blocname).getReadLine(s.getLocation());
		} else {
			this.includedSub = getSub(name).getReadLine(s.getLocation());
		}
		return this.readLine();
	}

	private ReadLine getReaderInclude(CharSequence2 s, final File f) {
		try {
			if (charset == null) {
				Log.info("Using default charset");
				return new ReadLineReader(new FileReader(f), f.getAbsolutePath(), s.getLocation());
			}
			Log.info("Using charset " + charset);
			return new ReadLineReader(new InputStreamReader(new FileInputStream(f), charset), f.getAbsolutePath(),
					s.getLocation());
		} catch (IOException e) {
			return new ReadLineSimple(s, e.toString());
		}

	}

	private CharSequence2 manageStartsub(Matcher2 m) throws IOException {
		final String name = m.group(1);
		this.learningSub = getSub(name);
		return this.readLine();
	}

	private CharSequence2 manageEndsub(Matcher2 m) throws IOException {
		this.learningSub = null;
		return this.readLine();
	}

	Sub getSub(String name) {
		Sub result = subs.get(name);
		if (result == null) {
			result = new Sub(name);
			subs.put(name, result);
		}
		return result;
	}

	public void close() throws IOException {
		source.close();
	}

}