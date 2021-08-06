package org.jenkinsci.plugins.IBM_zOS_Connector;


import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
import jenkins.util.xml.XMLUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * <h2>SCLMChangeLogParser</h2>
 * <p>
 * Parser of changelog.xml from SCLM.
 *
 * @author <a href="mailto:candiduslynx@gmail.com">Alexander Shcherbakov</a>
 * @version 1.0
 * @see SCLMChangeLogSet
 * @see LogSet
 * @see ChangeLogParser
 */
public class SCLMChangeLogParser extends ChangeLogParser {
    /**
     * Parse changelog.xml.
     *
     * @param build         Current build.
     * @param browser       Current browser.
     * @param changelogFile changelog.xml
     * @return SCLMChangeLogSet from changelog.xml.
     */
    @Override
    public SCLMChangeLogSet parse(Run build, RepositoryBrowser<?> browser,
                                  File changelogFile)
            throws IOException,
            SAXException {
        Element logE;
        // read file
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(changelogFile), StandardCharsets.UTF_8);
            logE = XMLUtils.parse(reader).getDocumentElement();
            reader.close();
        } catch (IOException | SAXException e) {
            throw new IOException("Failed to parse " + changelogFile, e);
        }

        NodeList logNL = logE.getChildNodes();
        Node changelog = null;

        for (int i = 0; i < logNL.getLength(); i++) {
            Node n = logNL.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("changelog")) {
                changelog = n;
                break;
            }
        }

        if (changelog == null) {
            throw new SAXException("File " + changelogFile + " is not a valid changelog file");
        }

        // Have proper node, scan entries.
        LogSet temp = new LogSet();

        NodeList entries = changelog.getChildNodes();
        for (int i = 0; i < entries.getLength(); i++) {
            Node elItem = entries.item(i);
            if (elItem.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!elItem.getNodeName().equals("entry")) {
                continue;
            }
            Element entryElement = (Element) elItem;
            LogSet.Entry e = new LogSet.Entry();
            NodeList entryNL = entryElement.getChildNodes();

            for (int j = 0; j < entryNL.getLength(); j++) {
                Node item = entryNL.item(j);
                if (item.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element otherE = (Element) item;
                String text = otherE.getTextContent();
                switch (otherE.getTagName()) {
                    case "date":
                        e.setChangeDate(text);
                        break;
                    case "project":
                        e.setProject(text);
                        break;
                    case "alternate":
                        e.setAlternate(text);
                        break;
                    case "group":
                        e.setGroup(text);
                        break;
                    case "type":
                        e.setType(text);
                        break;
                    case "name":
                        e.setName(text);
                        break;
                    case "version":
                        e.setVersion(text);
                        break;
                    case "userID":
                        e.setUserID(text);
                        break;
                    case "changeGroup":
                        e.setChangeGroup(text);
                        break;
                    case "editType":
                        e.setEditType(text);
                        break;
                    default:
                        throw new SAXException("Unexpected node " + otherE.getTagName());
                }
                temp.addEntry(e);
            }
        }

        // Convert to SCLMChangeLogSet
        SCLMChangeLogSet res = new SCLMChangeLogSet(build, browser);
        res.fromLogSet(temp);
        return res;
    }
}
