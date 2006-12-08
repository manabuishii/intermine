package org.intermine.web;

/*
 * Copyright (C) 2002-2005 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.log4j.Logger;

import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.query.Query;

/**
 * A template query, which consists of a PathQuery, description, category,
 * short name.
 *
 * @author Mark Woodbridge
 * @author Thomas Riley
 */
public class TemplateQuery extends PathQuery
{
    private static final Logger LOG = Logger.getLogger(TemplateQuery.class);

    /** Template query name. */
    protected String name;
    /** Template query title. */
    protected String title;
    /** Template query description. */
    protected String description;
    /** The private comment for this query. */
    protected String comment;
    /** Nodes with templated constraints */
    protected List editableNodes = new ArrayList();
    /** Map from node to editable constraint list */
    protected Map constraints = new HashMap();
    /** True if template is considered 'important' for a related class. */
    protected boolean important = false;
    /** Keywords set for this template. */
    protected String keywords = "";
    /** Edited version of another template */
    protected boolean edited = false;
    /** Map from editable constraints to Lists of possible values */
    protected Map possibleValues = new HashMap();

    /**
     * Construct a new instance of TemplateQuery.
     *
     * @param name the name of the template
     * @param title the short title of this template for showing in list
     * @param description the full template description for showing on the template form
     * @param comment an optional private comment for this template
     * @param query the query itself
     * @param important true if template is important
     * @param keywords keywords for this template
     */
    public TemplateQuery(String name, String title, String description, String comment,
                         PathQuery query, boolean important, String keywords) {
        super((PathQuery) query.clone());
        if (description != null) {
            this.description = description;
        }
        if (name != null) {
            this.name = name;
        }
        if (keywords != null) {
            this.keywords = keywords;
        }
        this.title = title;
        this.comment = comment;
        this.important = important;
    }

    /**
     * For a PathNode with editable constraints, get all the editable
     * constraints as a List.
     *
     * @param node  a PathNode with editable constraints
     * @return      List of Constraints for Node
     */
    public List getEditableConstraints(PathNode node) {
        return getEditableConstraints(node.getPath());
    }

    /**
     * Return all constrains for a given node or an empty list if none
     * For a Path with editable constraints, get all the editable
     * constraints as a List.
     *
     * @param node  a PathNode with editable constraints
     * @return      List of Constraints for Node
     */
    public List getEditableConstraints(String path) {
        if (nodes.get(path) == null) {
            return Collections.EMPTY_LIST;          
        } else {
            List ecs = new ArrayList();
            Iterator cIter = ((PathNode) nodes.get(path)).getConstraints().iterator();
            while (cIter.hasNext()) {
                Constraint c = (Constraint) cIter.next();
                if (c.isEditable()) {
                    ecs.add(c);
                }
            }
            return ecs;
        }
    }

    /**
     * Return a clone of this template query with all editable constraints
     * removed - i.e. a query that will return all possible results of executing
     * the template.  The original template is left unaltered.
     *
     * @return a clone of the original tempate without editable constraints.
     */
    public TemplateQuery cloneWithoutEditableConstraints() {
        TemplateQuery clone = (TemplateQuery) this.clone();

        // Find the editable constraints in the query.
        Iterator iter = clone.getNodes().entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            PathNode node = (PathNode) entry.getValue();
            Iterator citer = node.getConstraints().iterator();
            while (citer.hasNext()) {
                Constraint c = (Constraint) citer.next();
                if (c.isEditable()) {
                    citer.remove();
                }
            }
        }
        return clone;
    }


    /**
     * Return a List of all the Constraints of fields in this template query.
     *
     * @return a List of all the Constraints of fields in this template query
     */
    public List getAllEditableConstraints() {
        List ecs = new ArrayList();
        Iterator nodeIter = nodes.keySet().iterator();
        while (nodeIter.hasNext()) {
            ecs.addAll(getEditableConstraints((String) nodeIter.next()));
        }
        return ecs;
    }

    /**
     * Get the template title.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * Get the template description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the private comment for this template.
     * @return the description
     */
    public String getComment() {
        return comment;
    }

    /**
     * Get the nodes from the description, in order (eg. {Company.name}).
     *
     * @return the nodes
     */
    public List getEditableNodes() {
        List editableNodes = new ArrayList();
        Iterator nodeIter = nodes.values().iterator();
        while (nodeIter.hasNext()) {
            PathNode node = (PathNode) nodeIter.next();
            if (!(getEditableConstraints(node).isEmpty())) {
                editableNodes.add(node);
            }
        }
        return editableNodes;
    }

    /**
     * Get the query short name.
     *
     * @return the query identifier string
     */
    public String getName() {
        return name;
    }

    /**
     * @return true if template is important
     */
    public boolean isImportant() {
        return important;
    }

    /**
     * Get the keywords.
     *
     * @return template keywords
     */
    public String getKeywords() {
        return keywords;
    }

    /**
     * Returns a List of possible values for a node.
     *
     * @param node a PathNode
     * @return a List, or null if possible values have not been computed
     */
    public List getPossibleValues(PathNode node) {
        return (List) possibleValues.get(node);
    }

    /**
     * Returns true if there is any possibleValues data at all.
     *
     * @return a boolean
     */
    public boolean isSummarised() {
        return !possibleValues.isEmpty();
    }

    /**
     * Populates the possibleValues data for this TemplateQuery from the os.
     *
     * @param os an ObjectStore
     * @throws ObjectStoreException if something goes wrong
     */
    public void summarise(ObjectStore os) throws ObjectStoreException {
        Iterator iter = getEditableNodes().iterator();
        while (iter.hasNext()) {
            PathNode node = (PathNode) iter.next();
            Query q = TemplateHelper.getPrecomputeQuery(this, null, node);
            LOG.error("Running query: " + q);
            List results = os.execute(q, 0, 20, true, false, os.getSequence());
            if (results.size() < 20) {
                List values = new ArrayList();
                Iterator resIter = results.iterator();
                while (resIter.hasNext()) {
                    values.add(((List) resIter.next()).get(0));
                }
                possibleValues.put(node, values);
            }
        }
        LOG.error("New summary: " + possibleValues);
    }

    /**
     * Convert a template query to XML.
     *
     * @return this template query as XML.
     */
    public String toXml() {
        StringWriter sw = new StringWriter();
        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(sw);
            TemplateQueryBinding.marshal(this, writer);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }

        return sw.toString();
    }

    /**
     * Clone this TemplateQuery.
     *
     * @return a TemplateQuery
     */
    public Object clone() {
        TemplateQuery templateQuery = new TemplateQuery(name, title, description, comment,
                                                        (PathQuery) super.clone(), important,
                                                        keywords);
        templateQuery.edited = edited;
        return templateQuery;
    }

    /**
     * Return the PathQuery part of the TemplateQuery.
     *
     * @return a PathQuery
     */
    public PathQuery getPathQuery() {
        return (PathQuery) super.clone();
    }
    
    /**
     * Returns true if the TemplateQuery has been edited by the user and is therefore saved only in
     * the query history.
     *
     * @return a boolean
     */
    public boolean isEdited() {
        return edited;
    }
    
    /**
     * Set the query as being edited.
     *
     * @param edited whether the TemplateQuery has been modified by the user
     */
    public void setEdited(boolean edited) {
        this.edited = edited;
    }

}
