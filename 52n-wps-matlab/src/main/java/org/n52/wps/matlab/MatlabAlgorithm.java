/**
 * Copyright (C) 2013-2015 52°North Initiative for Geospatial Open Source
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
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.wps.matlab;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.opengis.wps.x100.ProcessDescriptionType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.n52.matlab.connector.MatlabException;
import org.n52.matlab.connector.MatlabRequest;
import org.n52.matlab.connector.MatlabResult;
import org.n52.matlab.connector.client.MatlabClient;
import org.n52.matlab.connector.value.MatlabScalar;
import org.n52.matlab.connector.value.MatlabValue;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.io.data.IData;
import org.n52.wps.matlab.description.MatlabProcessDescription;
import org.n52.wps.matlab.description.MatlabProcessInputDescription;
import org.n52.wps.matlab.transform.MatlabValueTransformer;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.IAlgorithm;
import org.n52.wps.server.ProcessDescription;

import com.github.autermann.wps.commons.description.ows.OwsCodeType;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class MatlabAlgorithm implements IAlgorithm {
    private static final Logger LOG = LoggerFactory
            .getLogger(MatlabAlgorithm.class);
    private final MatlabProcessDescription matlabProcessDescription;
    private final ProcessDescription processDescription;

    public MatlabAlgorithm(MatlabProcessDescription description) {
        this.matlabProcessDescription = Preconditions.checkNotNull(description);
        processDescription = new ProcessDescription();
        ProcessDescriptionType xml = this.matlabProcessDescription.getProcessDescription();
        processDescription.addProcessDescriptionForVersion(xml, WPSConfig.VERSION_100);
        processDescription.addProcessDescriptionForVersion(processDescription.createProcessDescriptionV200fromV100(xml), WPSConfig.VERSION_200);
    }

    @Override
    public List<String> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public ProcessDescription getDescription() {
        return this.processDescription;
    }

    @Override
    public String getWellKnownName() {
        return this.matlabProcessDescription.getId().getValue();
    }

    @Override
    public Class<? extends IData> getInputDataType(String id) {
        return this.matlabProcessDescription.getInput(new OwsCodeType(id)).getBindingClass();
    }

    @Override
    public Class<? extends IData> getOutputDataType(String id) {
        return this.matlabProcessDescription.getOutput(new OwsCodeType(id)).getBindingClass();
    }

    @Override
    public boolean processDescriptionIsValid(String version) {
        return this.getDescription().getProcessDescriptionType(version).validate();
    }

    @Override
    public Map<String, IData> run(Map<String, List<IData>> inputData)
            throws ExceptionReport {
        try {
            MatlabRequest request = fromInputData(inputData);
            LOG.info("Executing Matlab request: {}", request);
            MatlabClient client = this.matlabProcessDescription.getClientProvider().get();
            MatlabResult result = client.execSync(request);
            LOG.info("Matlab result: {}", result);
            return toOutputData(result);
        } catch (IOException | MatlabException ex) {
            throw new ExceptionReport(ex.getMessage(),
                    ExceptionReport.REMOTE_COMPUTATION_ERROR, ex);
        }
    }

    private MatlabRequest fromInputData(Map<String, List<IData>> inputs)
            throws ExceptionReport {

        MatlabRequest req = new MatlabRequest(matlabProcessDescription.getFunction());

        MatlabValueTransformer t = new MatlabValueTransformer();

        for (MatlabProcessInputDescription in : matlabProcessDescription.getInputDescriptions()) {
            String id = in.getId().getValue();
            if (inputs.containsKey(id)) {
                req.addParameter(t.transform(in, inputs.get(id)));
            } else if (in.getOccurence().isRequired()) {
                throw new ExceptionReport("missing input " + in.getId(),
                                          ExceptionReport.MISSING_PARAMETER_VALUE);
            } else {
                req.addParameter(new MatlabScalar(Double.NaN));
            }
        }
        matlabProcessDescription.getOutputDescriptions().stream().forEach(out ->
            req.addResult(out.getId().getValue(), out.getMatlabType())
        );
        return req;
    }

    private Map<String, IData> toOutputData(MatlabResult result)
            throws ExceptionReport {
        Map<String, IData> map = Maps.newHashMap();
        MatlabValueTransformer t = new MatlabValueTransformer();
        for (String id : result.getResults().keySet()) {
            MatlabValue value = result.getResult(id);
            OwsCodeType codeType = new OwsCodeType(id);
            IData data = t.transform(matlabProcessDescription.getOutput(codeType), value);
            map.put(id, data);
        }
        return map;
    }
}
