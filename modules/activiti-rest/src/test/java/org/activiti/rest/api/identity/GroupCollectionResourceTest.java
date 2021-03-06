/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.activiti.rest.api.identity;

import java.util.ArrayList;
import java.util.List;

import org.activiti.engine.identity.Group;
import org.activiti.engine.test.Deployment;
import org.activiti.rest.BaseRestTestCase;
import org.activiti.rest.api.RestUrls;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;


/**
 * @author Frederik Heremans
 */
public class GroupCollectionResourceTest extends BaseRestTestCase {

  /**
   * Test getting all groups.
   */
  @Deployment
  public void testGetGroups() throws Exception {
    List<Group> savedGroups = new ArrayList<Group>();
    try {
      Group group1 = identityService.newGroup("testgroup1");
      group1.setName("Test group");
      group1.setType("Test type");
      identityService.saveGroup(group1);
      savedGroups.add(group1);
      
      Group group2 = identityService.newGroup("testgroup2");
      group2.setName("Another group");
      group2.setType("Another type");
      identityService.saveGroup(group2);
      savedGroups.add(group2);
      
      Group group3 = identityService.createGroupQuery().groupId("admin").singleResult();
      assertNotNull(group3);
      
      // Test filter-less
      String url = RestUrls.createRelativeResourceUrl(RestUrls.URL_GROUP_COLLECTION);
      assertResultsPresentInDataResponse(url, group1.getId(), group2.getId(), group3.getId());
      
      // Test based on name
      url = RestUrls.createRelativeResourceUrl(RestUrls.URL_GROUP_COLLECTION) + "?name=Test group";
      assertResultsPresentInDataResponse(url, group1.getId());
      
      // Test based on name like
      url = RestUrls.createRelativeResourceUrl(RestUrls.URL_GROUP_COLLECTION) +"?nameLike=" + encode("% group");
      assertResultsPresentInDataResponse(url, group2.getId(), group1.getId());
      
      // Test based on type
      url = RestUrls.createRelativeResourceUrl(RestUrls.URL_GROUP_COLLECTION) +"?type=Another type";
      assertResultsPresentInDataResponse(url, group2.getId());
      
      // Test based on group member
      url = RestUrls.createRelativeResourceUrl(RestUrls.URL_GROUP_COLLECTION) + "?member=kermit";
      assertResultsPresentInDataResponse(url, group3.getId());
      
      // Test based on potentialStarter
      String processDefinitionId = repositoryService.createProcessDefinitionQuery().processDefinitionKey("simpleProcess")
              .singleResult().getId();
      repositoryService.addCandidateStarterGroup(processDefinitionId, "admin");
     
      url = RestUrls.createRelativeResourceUrl(RestUrls.URL_GROUP_COLLECTION) + "?potentialStarter=" + processDefinitionId;
      assertResultsPresentInDataResponse(url, group3.getId());
      
      
    } finally {
      
      // Delete groups after test passes or fails
      if(savedGroups.size() > 0) {
        for(Group group : savedGroups) {
          identityService.deleteGroup(group.getId());
        }
      }
    }
  }
  
  public void testCreateGroup() throws Exception {
    try {
      ObjectNode requestNode = objectMapper.createObjectNode();
      requestNode.put("id", "testgroup");
      requestNode.put("name", "Test group");
      requestNode.put("type", "Test type");
      
      ClientResource client = getAuthenticatedClient(RestUrls.createRelativeResourceUrl(RestUrls.URL_GROUP_COLLECTION));
      Representation response = client.post(requestNode);
      assertEquals(Status.SUCCESS_CREATED, client.getResponse().getStatus());
      
      JsonNode responseNode = objectMapper.readTree(response.getStream());
      assertNotNull(responseNode);
      assertEquals("testgroup", responseNode.get("id").getTextValue());
      assertEquals("Test group", responseNode.get("name").getTextValue());
      assertEquals("Test type", responseNode.get("type").getTextValue());
      assertTrue(responseNode.get("url").getTextValue().endsWith(RestUrls.createRelativeResourceUrl(RestUrls.URL_GROUP, "testgroup")));
      
      assertNotNull(identityService.createGroupQuery().groupId("testgroup").singleResult());
    } finally {
      try {
        identityService.deleteGroup("testgroup");
      } catch(Throwable t) {
        // Ignore, user might not have been created by test
      }
    }
  }
  
  public void testCreateGroupExceptions() throws Exception {
    ClientResource client = getAuthenticatedClient(RestUrls.createRelativeResourceUrl(RestUrls.URL_GROUP_COLLECTION));
    
    // Create without ID
    ObjectNode requestNode = objectMapper.createObjectNode();
    requestNode.put("name", "Test group");
    requestNode.put("type", "Test type");
    
    try {
      client.post(requestNode);
      fail("Exception expected");
    } catch(ResourceException expected) {
      assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, expected.getStatus());
      assertEquals("Id cannot be null.", expected.getStatus().getDescription());
    }
    
    // Create when group already exists
    requestNode = objectMapper.createObjectNode();
    requestNode.put("id", "admin");
    
    try {
      client.post(requestNode);
      fail("Exception expected");
    } catch(ResourceException expected) {
      assertEquals(Status.CLIENT_ERROR_CONFLICT, expected.getStatus());
      assertEquals("A group with id 'admin' already exists.", expected.getStatus().getDescription());
    }
  }
}
