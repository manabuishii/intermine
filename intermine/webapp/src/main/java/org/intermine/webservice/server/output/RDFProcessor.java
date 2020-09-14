package org.intermine.webservice.server.output;

/*
 * Copyright (C) 2002-2020 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.intermine.api.profile.Profile;
import org.intermine.api.results.ResultElement;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.FieldDescriptor;
import org.intermine.metadata.AttributeDescriptor;
import org.intermine.pathquery.Path;
import org.intermine.pathquery.PathException;
import org.intermine.web.logic.PermanentURIHelper;
import org.intermine.web.uri.InterMineLUI;
import org.intermine.web.uri.InterMineLUIConverter;
import org.intermine.webservice.server.core.ResultProcessor;
import org.intermine.webservice.server.exceptions.BadRequestException;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

/**
 * A class that defines the basic methods for processing RDF results.
 * @author Daniela Butano
 *
 */
public class RDFProcessor extends ResultProcessor
{
    private String uri;
    private InterMineLUIConverter luiConverter;
    private static final String VOC_NAMESPACE = "http://intermine.org/vocabulary/";
    private static final String RES_NAMESPACE = "http://intermine.org/resource/";

    /**
     * Constructor
     * @param request the http request
     * @param  profile the user profile
     *
     */
    public RDFProcessor(HttpServletRequest request, Profile profile) {
        uri = (new PermanentURIHelper(request)).getPermanentBaseURI();
        uri = (!uri.endsWith("/")) ? uri.concat("/") : uri;
        luiConverter = new InterMineLUIConverter(profile);
    }

    @Override
    public void write(Iterator<List<ResultElement>> resultIt, Output output) {
        Model model = ModelFactory.createDefaultModel();

        while (resultIt.hasNext())  {
            Resource resource = null;
            ClassDescriptor classDescriptor = null;
            List<ResultElement> row = resultIt.next();
            Integer id;
            ClassDescriptor currentClassDesc = null;
            Map<String, Resource> resources = new HashMap<>();

            for (ResultElement item : row) {
                Path path = item.getPath();
                classDescriptor = path.getLastClassDescriptor();

                if (currentClassDesc == null || currentClassDesc != classDescriptor) {
                    currentClassDesc = classDescriptor;
                    id = item.getId();
                    InterMineLUI lui = luiConverter.getInterMineLUI(id);

                    String resourceURI = (lui != null) ? uri.concat(lui.toString())
                            : RES_NAMESPACE.concat(id.toString());
                    if (classDescriptor.getOntologyTerm() != null) {
                        resource = model.createResource(resourceURI,
                                model.createResource(classDescriptor.getOntologyTerm()));
                    } else {
                        resource = model.createResource(resourceURI,
                                model.createResource(RES_NAMESPACE
                                        + classDescriptor.getSimpleName()));
                    }
                    resources.put(currentClassDesc.getName(), resource);
                    if (!resources.isEmpty()) {
                        String stringPath = path.toString();
                        stringPath = stringPath.substring(0, stringPath.lastIndexOf("."));
                        Path partialPath = null;

                        try {
                            partialPath = new Path(
                                    org.intermine.metadata.Model
                                            .getInstanceByName("genomic"), stringPath);
                            if (partialPath.endIsReference() || partialPath.endIsCollection()) {
                                FieldDescriptor rd = partialPath.getEndFieldDescriptor();
                                String parentToLink =
                                        partialPath.getSecondLastClassDescriptor().getName();
                                Resource parentResource = resources.get(parentToLink);
                                String localName = "has" + rd.getName();
                                parentResource.addProperty(
                                        model.createProperty(VOC_NAMESPACE, localName), resource);
                            }
                        } catch (PathException pe) {
                            throw new BadRequestException(stringPath + " is not a valid path");
                        }
                    }
                }
                FieldDescriptor fd = path.getEndFieldDescriptor();
                if (fd.isAttribute() && item.getField() != null) {
                    String ontologyTerm = ((AttributeDescriptor) fd).getOntologyTerm();
                    if (ontologyTerm != null) {
                        resource.addProperty(model.createProperty(ontologyTerm),
                                item.getField().toString());
                    }
                }
            }

        }
        ((RDFOutput) output).addResultItem(model);
    }
}
