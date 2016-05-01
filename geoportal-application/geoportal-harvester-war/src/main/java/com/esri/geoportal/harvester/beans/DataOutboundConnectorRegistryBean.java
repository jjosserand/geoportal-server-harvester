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
package com.esri.geoportal.harvester.beans;

import com.esri.geoportal.harvester.engine.DataOutboundConnectorRegistry;
import com.esri.geoportal.harvester.folder.FolderConnector;
import com.esri.geoportal.harvester.folder.FolderDefinition;
import com.esri.geoportal.harvester.gpt.GptConnector;
import com.esri.geoportal.harvester.gpt.GptDefinition;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;

/**
 * Data outbound connector registry bean.
 */
@Service
public class DataOutboundConnectorRegistryBean extends DataOutboundConnectorRegistry {
  
  @PostConstruct
  public void init() {
    put(FolderDefinition.TYPE, new FolderConnector());
    put(GptDefinition.TYPE, new GptConnector());
  }
}
