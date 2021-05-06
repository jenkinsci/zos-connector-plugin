package org.jenkinsci.plugins.IBM_zOS_Connector;


import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
import org.apache.commons.digester3.Digester;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * <h2>SCLMChangeLogParser</h2>
 *
 * Parser of changelog.xml from SCLM.
 *
 * @author <a href="mailto:candiduslynx@gmail.com">Alexander Shcherbakov</a>
 *
 * @version 1.0
 *
 * @see SCLMChangeLogSet
 * @see LogSet
 * @see ChangeLogParser
 */
public class SCLMChangeLogParser extends ChangeLogParser {
    /**
     * Parse changelog.xml.
     *
     * @param build Current build.
     * @param browser Current browser.
     * @param changelogFile changelog.xml
     *
     * @return SCLMChangeLogSet from changelog.xml.
     *
     */
    @Override
    public SCLMChangeLogSet parse(Run build, RepositoryBrowser<?> browser,
                                                            File changelogFile)
        throws IOException,
        SAXException
    {
        Digester digester = new Digester();

        digester.setXIncludeAware(false);

        if (!Boolean.getBoolean(SCLMChangeLogParser.class.getName() + ".UNSAFE")) {
            try {
                digester.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                digester.setFeature("http://xml.org/sax/features/external-general-entities", false);
                digester.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                digester.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            }
            catch ( ParserConfigurationException ex) {
                throw new SAXException("Failed to securely configure Zos changelog parser", ex);
            }
        }

        // Parse fields.
        digester.addObjectCreate("*/changelog", LogSet.class);
        digester.addSetProperties("*/changelog");
        digester.addObjectCreate("*/changelog/entry", LogSet.Entry.class);
        digester.addSetProperties("*/changelog/entry");
        digester.addBeanPropertySetter("*/changelog/entry/date", "changeDate");
        digester.addBeanPropertySetter("*/changelog/entry/project");
        digester.addBeanPropertySetter("*/changelog/entry/alternate");
        digester.addBeanPropertySetter("*/changelog/entry/group");
        digester.addBeanPropertySetter("*/changelog/entry/type");
        digester.addBeanPropertySetter("*/changelog/entry/name");
        digester.addBeanPropertySetter("*/changelog/entry/version");
        digester.addBeanPropertySetter("*/changelog/entry/userID");
        digester.addBeanPropertySetter("*/changelog/entry/changeGroup");
        digester.addBeanPropertySetter("*/changelog/entry/editType");
        digester.addSetNext("*/changelog/entry", "addEntry");

        // Do the actual parsing
        InputStreamReader reader = new InputStreamReader(new FileInputStream(changelogFile), StandardCharsets.UTF_8);
        LogSet temp = digester.parse(reader);
        reader.close();

        // Convert to SCLMChangeLogSet
        SCLMChangeLogSet res = new SCLMChangeLogSet(build,browser);
        res.fromLogSet(temp);
        return res;
    }
}
