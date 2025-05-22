/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.am.integration.tests.apimGovernance;

import com.google.gson.Gson;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.am.integration.clients.governance.ApiException;
import org.wso2.am.integration.clients.governance.ApiResponse;
import org.wso2.am.integration.clients.governance.api.dto.RulesetInfoDTO;
import org.wso2.am.integration.clients.governance.api.dto.RulesetListDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ErrorDTO;
import org.wso2.am.integration.test.Constants.APIMGovernanceTestConstants;
import org.wso2.am.integration.test.utils.base.APIMIntegrationBaseTest;
import org.wso2.carbon.automation.engine.annotations.ExecutionEnvironment;
import org.wso2.carbon.automation.engine.annotations.SetEnvironment;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.test.utils.common.TestConfigurationProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * This class contains the test cases for the Ruleset Management API.
 */
@SetEnvironment(executionEnvironments = {ExecutionEnvironment.STANDALONE})
public class RulesetMgtTestCase extends APIMIntegrationBaseTest {

    private final List<String> defaultRulesets = new ArrayList<>();
    private String resourcePath;
    private String createdRulesetId;

    @Factory(dataProvider = "userModeDataProvider")
    public RulesetMgtTestCase(TestUserMode userMode) {

        this.userMode = userMode;
    }

    @DataProvider
    public static Object[][] userModeDataProvider() {

        return new Object[][]{
                {TestUserMode.SUPER_TENANT_ADMIN},
                {TestUserMode.TENANT_ADMIN},
        };
    }

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {

        super.init(userMode);
        defaultRulesets.add(APIMGovernanceTestConstants.DEFAULT_RULESET_WSO2_API);
        defaultRulesets.add(APIMGovernanceTestConstants.DEFAULT_RULESET_WSO2_REST);
        defaultRulesets.add(APIMGovernanceTestConstants.DEFAULT_RULESET_OWASP);

        resourcePath =
                TestConfigurationProvider.getResourceLocation() + APIMGovernanceTestConstants.TEST_RESOURCE_DIRECTORY
                + File.separator;
    }

    /**
     * This tests whether the default rulesets have been created within the organization.
     */
    @Test(groups = {"wso2.am"}, description = "Test retrieval of default rulesets")
    public void testDefaultRulesetRetrieval() throws Exception {
        ApiResponse<RulesetListDTO> rulesets = restAPIGovernance.getRulesets(10,0,"");
        assertEquals(rulesets.getStatusCode(), Response.Status.OK.getStatusCode(),
                "Error in retrieving default APIM governance rulesets");
        List<RulesetInfoDTO> obtainedRulesets = rulesets.getData().getList();
        assertNotNull(obtainedRulesets, "No default APIM governance rulesets found");
        List<String> obtainedRulesetNames = obtainedRulesets.stream().map(RulesetInfoDTO::getName)
                .collect(Collectors.toList());
        for (String defaultRuleset : defaultRulesets) {
            boolean found = obtainedRulesetNames.contains(defaultRuleset);
            assertTrue(found, "Default APIM governance ruleset " + defaultRuleset + " not found");
        }
    }


    /**
     * This tests the creation of an invalid ruleset.
     * @throws Exception If an error occurs while creating the ruleset.
     */
    @Test(groups = {"wso2.am"}, description = "Test invalid ruleset creation",
            dependsOnMethods = "testDefaultRulesetRetrieval")
    public void testInvalidRulesetCreation() throws Exception {
        File rulesetContentFile = readYamlFileIntoTempFile(resourcePath +
                APIMGovernanceTestConstants.INVALID_SPECTRAL_RULESET_FILE_NAME);

        try{
            restAPIGovernance.createRuleset(APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_NAME,
                    rulesetContentFile, APIMGovernanceTestConstants.API_DEFINITION_RULE_TYPE,
                    APIMGovernanceTestConstants.REST_API_ARTIFACT_TYPE,
                    APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_DESCRIPTION,
                    APIMGovernanceTestConstants.SPECTRAL_RULE_CATEGORY,
                    APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_DOCUMENTATION_LINK,
                    APIMGovernanceTestConstants.ADMIN_PROVIDER);
            fail("Invalid ruleset creation should have failed");
        } catch (ApiException e) {
            Assert.assertEquals(e.getCode(), Response.Status.BAD_REQUEST.getStatusCode(),
                    "Invalid ruleset creation should have failed with a 400 error");
            ErrorDTO errorDTO = new Gson().fromJson(e.getResponseBody(), ErrorDTO.class);
            Assert.assertEquals(errorDTO.getCode().longValue(), 990120, "Invalid error code " +
                    "for invalid ruleset creation");
        }
    }

    /**
     * This tests the creation of a new valid ruleset.
     * @throws Exception If an error occurs while creating the ruleset.
     */
    @Test(groups = {"wso2.am"}, description = "Test valid ruleset creation",
            dependsOnMethods = "testInvalidRulesetCreation")
    public void testValidRulesetCreation() throws Exception {

        File rulesetContentFile = readYamlFileIntoTempFile(resourcePath +
                APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_FILE_NAME);

        ApiResponse<RulesetInfoDTO> response = restAPIGovernance
                .createRuleset(APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_NAME, rulesetContentFile,
                        APIMGovernanceTestConstants.API_DEFINITION_RULE_TYPE,
                        APIMGovernanceTestConstants.REST_API_ARTIFACT_TYPE,
                        APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_DESCRIPTION,
                        APIMGovernanceTestConstants.SPECTRAL_RULE_CATEGORY,
                        APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_DOCUMENTATION_LINK,
                        APIMGovernanceTestConstants.ADMIN_PROVIDER);
        assertEquals(response.getStatusCode(), Response.Status.CREATED.getStatusCode(),
                "Error while creating new APIM governance ruleset");
        createdRulesetId = response.getData().getId();
        assertNotNull(createdRulesetId, "Error while creating new APIM governance ruleset");
    }

    /**
     * This tests the update of an existing ruleset with invalid content.
     * @throws Exception If an error occurs while updating the ruleset.
     */
    @Test(groups = {"wso2.am"}, description = "Test invalid ruleset update", dependsOnMethods =
            "testValidRulesetCreation")
    public void testInvalidValidRulesetUpdate() throws Exception {
        File rulesetContentFile = readYamlFileIntoTempFile(resourcePath +
                APIMGovernanceTestConstants.INVALID_SPECTRAL_RULESET_FILE_NAME);

        try{
            restAPIGovernance.updateRuleset(createdRulesetId,
                    APIMGovernanceTestConstants.UPDATED_SIMPLE_SPECTRAL_RULESET_NAME,
                    rulesetContentFile,
                    APIMGovernanceTestConstants.API_DEFINITION_RULE_TYPE,
                    APIMGovernanceTestConstants.REST_API_ARTIFACT_TYPE,
                    APIMGovernanceTestConstants.UPDATED_SIMPLE_SPECTRAL_RULESET_DESCRIPTION,
                    APIMGovernanceTestConstants.SPECTRAL_RULE_CATEGORY,
                    APIMGovernanceTestConstants.UPDATED_SIMPLE_SPECTRAL_RULESET_DOCUMENTATION_LINK,
                    APIMGovernanceTestConstants.ADMIN_PROVIDER);
            fail("Invalid ruleset update should have failed");
        } catch (ApiException e) {
            Assert.assertEquals(e.getCode(), Response.Status.BAD_REQUEST.getStatusCode(),
                    "Invalid ruleset update should have failed with a 400 error");
            ErrorDTO errorDTO = new Gson().fromJson(e.getResponseBody(), ErrorDTO.class);
            Assert.assertEquals(errorDTO.getCode().longValue(), 990120, "Invalid error code " +
                    "for invalid ruleset update");
        }
    }

    /**
     * This tests the update of an existing ruleset.
     * @throws Exception If an error occurs while updating the ruleset.
     */
    @Test(groups = {"wso2.am"}, description = "Test valid ruleset update", dependsOnMethods =
            "testInvalidValidRulesetUpdate")
    public void testValidRulesetUpdate() throws Exception{
        File rulesetContentFile = readYamlFileIntoTempFile(resourcePath +
                APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_FILE_NAME);

        ApiResponse<RulesetInfoDTO> response = restAPIGovernance
                .updateRuleset(createdRulesetId,
                        APIMGovernanceTestConstants.UPDATED_SIMPLE_SPECTRAL_RULESET_NAME,
                        rulesetContentFile,
                        APIMGovernanceTestConstants.API_DEFINITION_RULE_TYPE,
                        APIMGovernanceTestConstants.REST_API_ARTIFACT_TYPE,
                        APIMGovernanceTestConstants.UPDATED_SIMPLE_SPECTRAL_RULESET_DESCRIPTION,
                        APIMGovernanceTestConstants.SPECTRAL_RULE_CATEGORY,
                        APIMGovernanceTestConstants.UPDATED_SIMPLE_SPECTRAL_RULESET_DOCUMENTATION_LINK,
                        APIMGovernanceTestConstants.ADMIN_PROVIDER);
        assertEquals(response.getStatusCode(), Response.Status.OK.getStatusCode(),
                "Error while updating APIM governance ruleset");
        assertEquals(response.getData().getName(), APIMGovernanceTestConstants.UPDATED_SIMPLE_SPECTRAL_RULESET_NAME,
                "Error while updating APIM governance ruleset name");
        assertEquals(response.getData().getDescription(), APIMGovernanceTestConstants
                        .UPDATED_SIMPLE_SPECTRAL_RULESET_DESCRIPTION,
                "Error while updating APIM governance ruleset description");
        assertEquals(response.getData().getDocumentationLink(), APIMGovernanceTestConstants
                        .UPDATED_SIMPLE_SPECTRAL_RULESET_DOCUMENTATION_LINK,
                "Error while updating APIM governance ruleset documentation link");
    }

    /**
     * This tests the deletion of an existing ruleset.
     * @throws Exception If an error occurs while deleting the ruleset.
     */
    @Test(groups = {"wso2.am"}, description = "Test valid ruleset deletion", dependsOnMethods =
            "testValidRulesetUpdate")
    public void testValidRulesetDeletion() throws Exception {
        ApiResponse<Void> response = restAPIGovernance.deleteRuleset(createdRulesetId);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatusCode(),
                "Error while deleting APIM governance ruleset");
    }


    /**
     * This tests the creation of a new valid ruleset with JSON content.
     * @throws Exception If an error occurs while creating the ruleset.
     */
    @Test(groups = {"wso2.am"}, description = "Test valid ruleset creation with JSON content", dependsOnMethods =
            "testValidRulesetDeletion")
    public void testValidRulesetCreationWithJsonContent() throws Exception {
        File rulesetContentFile = new File(resourcePath + APIMGovernanceTestConstants
                .SIMPLE_SPECTRAL_RULESET_JSON_FILE_NAME);
        ApiResponse<RulesetInfoDTO> response = restAPIGovernance
                .createRuleset(APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_NAME, rulesetContentFile,
                        APIMGovernanceTestConstants.API_DEFINITION_RULE_TYPE,
                        APIMGovernanceTestConstants.REST_API_ARTIFACT_TYPE,
                        APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_DESCRIPTION,
                        APIMGovernanceTestConstants.SPECTRAL_RULE_CATEGORY,
                        APIMGovernanceTestConstants.SIMPLE_SPECTRAL_RULESET_DOCUMENTATION_LINK,
                        APIMGovernanceTestConstants.ADMIN_PROVIDER);
        assertEquals(response.getStatusCode(), Response.Status.CREATED.getStatusCode(),
                "Error while creating new APIM governance ruleset with JSON content");
        createdRulesetId = response.getData().getId();
        assertNotNull(createdRulesetId, "Error while creating new APIM governance ruleset with JSON content");

        ApiResponse<Void> delResponse = restAPIGovernance.deleteRuleset(createdRulesetId);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), delResponse.getStatusCode(),
                "Error while deleting APIM governance ruleset with JSON content");
    }


    /**
     * This tests the behavior when a ruleset associated with a policy is deleted.
     * @throws Exception If an error occurs while deleting the ruleset.
     */
    @Test(groups = {"wso2.am"}, description = "Test invalid ruleset deletion", dependsOnMethods =
            "testValidRulesetCreationWithJsonContent")
    public void testInvalidRulesetDelAttachedToPolicy() throws Exception {
        // Create a test policy with default rulesets and a blocking action for deployt
        ApiResponse<RulesetListDTO> allRulesets = restAPIGovernance.getRulesets(10, 0, "");
        assertNotNull(allRulesets.getData().getList(), "Failed to retrieve rulesets in the organization");

        List<String> defaultRulesetIds = allRulesets.getData().getList().stream()
                .filter(ruleset -> defaultRulesets.contains(ruleset.getName()))
                .map(RulesetInfoDTO::getId).collect(Collectors.toList());

        String defaultRulesetId = defaultRulesetIds.get(0);

        try {
            restAPIGovernance.deleteRuleset(defaultRulesetId);
        } catch (ApiException e) {
            Assert.assertEquals(e.getCode(), Response.Status.CONFLICT.getStatusCode(),
                    "Invalid ruleset deletion should have failed with a 409 error");
            ErrorDTO errorDTO = new Gson().fromJson(e.getResponseBody(), ErrorDTO.class);
            Assert.assertEquals(errorDTO.getCode().longValue(), 990101, "Invalid error code " +
                    "for invalid ruleset deletion");
        }

    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        super.cleanUp();
    }


    /**
     * Reads a YAML file from the given path and writes it to a temporary file.
     * @param filePath The path of the YAML file to read.
     * @return A temporary file containing the YAML content.
     * @throws IOException If an error occurs while reading or writing the file.
     */
    private File readYamlFileIntoTempFile(String filePath) throws IOException {
        // Read the file content into a String
        String yamlContent = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);

        // Create a temp file and write the YAML content to it
        File tempFile = File.createTempFile("ruleset", ".yaml");
        tempFile.deleteOnExit();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(tempFile.toPath()),
                StandardCharsets.UTF_8))) {
            writer.write(yamlContent);
        }

        return tempFile;
    }

}
