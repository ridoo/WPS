/**
 * Copyright (C) 2007 - 2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * As an exception to the terms of the GPL, you may copy, modify,
 * propagate, and distribute a work formed by combining 52°North WPS
 * GeoTools Modules with the Eclipse Libraries, or a work derivative of
 * such a combination, even if such copying, modification, propagation, or
 * distribution would otherwise violate the terms of the GPL. Nothing in
 * this exception exempts you from complying with the GPL in all respects
 * for all of the code used other than the Eclipse Libraries. You may
 * include this exception and its grant of permissions when you distribute
 * 52°North WPS GeoTools Modules. Inclusion of this notice with such a
 * distribution constitutes a grant of such permissions. If you do not wish
 * to grant these permissions, remove this paragraph from your
 * distribution. "52°North WPS GeoTools Modules" means the 52°North WPS
 * modules using GeoTools functionality - software licensed under version 2
 * or any later version of the GPL, or a work based on such software and
 * licensed under the GPL. "Eclipse Libraries" means Eclipse Modeling
 * Framework Project and XML Schema Definition software distributed by the
 * Eclipse Foundation and licensed under the Eclipse Public License Version
 * 1.0 ("EPL"), or a work based on such software and licensed under the EPL.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.wps.io.datahandler.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.xml.namespace.QName;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollections;
import org.geotools.feature.GeometryAttributeImpl;
import org.geotools.feature.type.GeometryDescriptorImpl;
import org.geotools.feature.type.GeometryTypeImpl;
import org.geotools.filter.identity.GmlObjectIdImpl;
import org.geotools.gml2.GMLConfiguration;
import org.geotools.gml3.ApplicationSchemaConfiguration;
import org.geotools.xml.Configuration;
import org.n52.wps.io.SchemaRepository;
import org.n52.wps.io.data.binding.complex.GTVectorDataBinding;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.filter.identity.Identifier;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.vividsolutions.jts.geom.Geometry;

/**
 * This parser handles xml files compliant to GML2.
 * 
 * @author foerster
 * 
 */
public class GML2BasicParser extends AbstractParser {
	private static Logger LOGGER = LoggerFactory.getLogger(GML2BasicParser.class);

	public GML2BasicParser() {
		super();
		supportedIDataTypes.add(GTVectorDataBinding.class);
	}

	public GTVectorDataBinding parse(InputStream stream, String mimeType,
			String schema) {

		FileOutputStream fos = null;
		try {
			File tempFile = File.createTempFile(UUID.randomUUID().toString(),
					".gml2");
			finalizeFiles.add(tempFile); // mark for final delete
			fos = new FileOutputStream(tempFile);
			int i = stream.read();
			while (i != -1) {
				fos.write(i);
				i = stream.read();
			}
			fos.flush();
			fos.close();
			GTVectorDataBinding data = parseXML(tempFile);

			return data;
		} catch (IOException e) {
			if (fos != null)
				try {
					fos.close();
				} catch (Exception e1) {
				}
			throw new IllegalArgumentException("Error while creating tempFile",
					e);
		}
	}

	public GTVectorDataBinding parseXML(File file) {
		
		SimpleFeatureCollection fc = parseSimpleFeatureCollection(file);
		
		GTVectorDataBinding data = new GTVectorDataBinding(fc);

		return data;
	}
	
	public SimpleFeatureCollection parseSimpleFeatureCollection(File file) {
		QName schematypeTuple = determineFeatureTypeSchema(file);

		Configuration configuration = null;

		boolean shouldSetParserStrict = true;

		String schemaLocation = schematypeTuple.getLocalPart();

		if (schemaLocation != null && schematypeTuple.getNamespaceURI() != null) {
			SchemaRepository.registerSchemaLocation(
					schematypeTuple.getNamespaceURI(), schemaLocation);
			configuration = new ApplicationSchemaConfiguration(
					schematypeTuple.getNamespaceURI(), schemaLocation);
		} else {
			configuration = new GMLConfiguration();
			shouldSetParserStrict = false;
		}

		org.geotools.xml.Parser parser = new org.geotools.xml.Parser(
				configuration);

		// parse
		SimpleFeatureCollection fc = DefaultFeatureCollections.newCollection();
		try {
			Object parsedData = null;
			try {
				parser.setStrict(shouldSetParserStrict);
				parsedData = parser.parse(new FileInputStream(file));
			} catch (SAXException e5) {
				// assume the xsd containing the schema was not found
				configuration = new GMLConfiguration();
				parser = new org.geotools.xml.Parser(configuration);
				parser.setStrict(false);
				parsedData = parser.parse(new FileInputStream(file));
			}
			if (parsedData instanceof SimpleFeatureCollection) {
				fc = (SimpleFeatureCollection) parsedData;
			} else {
				List<?> possibleSimpleFeatureList = ((ArrayList<?>) ((HashMap<?, ?>) parsedData)
						.get("featureMember"));

				if (possibleSimpleFeatureList != null) {
					List<SimpleFeature> simpleFeatureList = new ArrayList<SimpleFeature>();

					SimpleFeatureType sft = null;

					for (Object possibleSimpleFeature : possibleSimpleFeatureList) {

						if (possibleSimpleFeature instanceof SimpleFeature) {
							SimpleFeature sf = ((SimpleFeature) possibleSimpleFeature);
							if (sft == null) {
								sft = sf.getType();
							}
							simpleFeatureList.add(sf);
						}
					}

					fc = new ListFeatureCollection(sft, simpleFeatureList);
				} else {
					fc = (SimpleFeatureCollection) ((HashMap<?, ?>) parsedData)
							.get("FeatureCollection");
				}
			}

			SimpleFeatureIterator featureIterator = fc.features();
			while (featureIterator.hasNext()) {
				SimpleFeature feature = (SimpleFeature) featureIterator.next();
				if (feature.getDefaultGeometry() == null) {
					Collection<org.opengis.feature.Property> properties = feature
							.getProperties();
					for (org.opengis.feature.Property property : properties) {
						try {

							Geometry g = (Geometry) property.getValue();
							if (g != null) {
								GeometryAttribute oldGeometryDescriptor = feature
										.getDefaultGeometryProperty();
								GeometryType type = new GeometryTypeImpl(
										property.getName(),
										(Class<?>) oldGeometryDescriptor
												.getType().getBinding(),
										oldGeometryDescriptor.getType()
												.getCoordinateReferenceSystem(),
										oldGeometryDescriptor.getType()
												.isIdentified(),
										oldGeometryDescriptor.getType()
												.isAbstract(),
										oldGeometryDescriptor.getType()
												.getRestrictions(),
										oldGeometryDescriptor.getType()
												.getSuper(),
										oldGeometryDescriptor.getType()
												.getDescription());

								GeometryDescriptor newGeometryDescriptor = new GeometryDescriptorImpl(
										type, property.getName(), 0, 1, true,
										null);
								Identifier identifier = new GmlObjectIdImpl(
										feature.getID());
								GeometryAttributeImpl geo = new GeometryAttributeImpl(
										(Object) g, newGeometryDescriptor,
										identifier);
								feature.setDefaultGeometryProperty(geo);
								feature.setDefaultGeometry(g);

							}
						} catch (ClassCastException e) {
							// do nothing
						}

					}
				}
			}
			return fc;
		} catch (Exception e) {
			LOGGER.error(
					"Exception while trying to parse GML2 FeatureCollection.",
					e);
			throw new RuntimeException(e);
		}
	}

	private QName determineFeatureTypeSchema(File file) {
		try {
			GML2Handler handler = new GML2Handler();
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.newSAXParser().parse(new FileInputStream(file),
					(DefaultHandler) handler);
			String schemaUrl = handler.getSchemaUrl();
			String namespaceURI = handler.getNameSpaceURI();
			return new QName(namespaceURI, schemaUrl);

		} catch (Exception e) {
			LOGGER.error(
					"Exception while trying to determining GML2 FeatureType schema.",
					e);
			throw new IllegalArgumentException(e);
		}
	}

}
