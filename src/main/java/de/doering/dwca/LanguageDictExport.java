package de.doering.dwca;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class LanguageDictExport {

  public static void main(String[] args) throws IOException{
    List<Locale> locales = Lists.newArrayList();
    Joiner join = Joiner.on(";").skipNulls();
    for (String iso : Locale.getISOLanguages()){
      locales.add(new Locale(iso));
    }

    System.out.println("ISO 639-1\tISO 639-2\tEnglish\tNative\tTranslations");
    for (Locale loc : locales){
      Set<String> displayNames = Sets.newHashSet();
      for (Locale l2 : locales){
        displayNames.add(loc.getDisplayLanguage(l2).toLowerCase());
      }
      System.out.println(String.format("%s\t%s\t%s\t%s\t%s", loc.getLanguage().toUpperCase(), loc.getISO3Language().toUpperCase(), loc.getDisplayLanguage(Locale.ENGLISH), loc.getDisplayLanguage(loc), join.join(displayNames)));
    }

  }
}
