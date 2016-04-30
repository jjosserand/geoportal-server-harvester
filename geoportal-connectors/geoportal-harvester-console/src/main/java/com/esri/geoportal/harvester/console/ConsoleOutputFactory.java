/*
 * Copyright 2016 Esri, Inc..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.esri.geoportal.harvester.console;

import com.esri.geoportal.harvester.api.DataConnector;
import com.esri.geoportal.harvester.api.DataBrokerUiTemplate;
import com.esri.geoportal.harvester.api.DataOutput;
import com.esri.geoportal.harvester.api.DataOutputFactory;

/**
 * Console output factory.
 */
public class ConsoleOutputFactory implements DataOutputFactory {

  @Override
  public DataOutput create(DataConnector connector) throws IllegalArgumentException {
    return new ConsoleDataDestination();
  }

  @Override
  public DataBrokerUiTemplate getTemplate() {
    return new DataBrokerUiTemplate("CONSOLE", "Console output", null);
  }
  
}