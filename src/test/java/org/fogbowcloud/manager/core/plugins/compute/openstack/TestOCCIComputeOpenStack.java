package org.fogbowcloud.manager.core.plugins.compute.openstack;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.OCCIComputeApplication;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Header;
import org.restlet.util.Series;

public class TestOCCIComputeOpenStack {

	private static final String RESTLET_HEADERS_ATT_KEY = "org.restlet.http.headers";
	private static final String FIRST_INSTANCE_ID = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private static final String FIFTH_INSTANCE_ID = "hla256kh-43ar-67ww-ao90-fa8d456fce9f";
	private static final String FOURTH_INSTANCE_ID = "qwuif8ad-19a3-4afg-1l77-tred90crei0q";
	private static final String THIRD_INSTANCE_ID = "cg2563ee-503c-6abr-54gl-ba8d12hf0pof";
	private static final String SECOND_INSTANCE_ID = "at62f3ad-67ac-56gb-8a55-adbm98cdee9f";

	private OpenStackOCCIComputePlugin occiComputeOpenStack;
	private PluginHelper pluginHelper;
	private List<String> expectedInstanceIds;
	private Token defaultToken;
	private List<Flavor> flavors;

	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_URL_KEY, PluginHelper.COMPUTE_OCCI_URL);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY, OCCIComputeApplication.INSTANCE_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY, OCCIComputeApplication.OS_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY, OCCIComputeApplication.RESOURCE_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY, OCCIComputeApplication.SMALL_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + PluginHelper.LINUX_X86_TERM, PluginHelper.CIRROS_IMAGE_TERM);

		occiComputeOpenStack = new OpenStackOCCIComputePlugin(properties);
		
		flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(OrderConstants.SMALL_TERM, "1", "1000", "10");
		flavorSmall.setId(SECOND_INSTANCE_ID);
		flavors.add(flavorSmall); 
		flavors.add(new Flavor("medium", "2", "2000", "20"));
		flavors.add(new Flavor("big", "4", "4000", "40"));
		occiComputeOpenStack.setFlavors(flavors );
		
		defaultToken = new Token(PluginHelper.ACCESS_ID, PluginHelper.USERNAME,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		
		// five first generated instance ids
		expectedInstanceIds = new ArrayList<String>();
		expectedInstanceIds.add(FIRST_INSTANCE_ID);
		expectedInstanceIds.add(SECOND_INSTANCE_ID);
		expectedInstanceIds.add(THIRD_INSTANCE_ID);
		expectedInstanceIds.add(FOURTH_INSTANCE_ID);
		expectedInstanceIds.add(FIFTH_INSTANCE_ID);

		pluginHelper = new PluginHelper();
		pluginHelper.initializeOCCIComputeComponent(expectedInstanceIds);
	}

	@After
	public void tearDown() throws Exception {
		pluginHelper.disconnectComponent();
	}

	@Test
	public void testRequestAValidInstance() {
		List<Category> categories = new ArrayList<Category>();
		
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);

		Assert.assertEquals(FIRST_INSTANCE_ID, occiComputeOpenStack.requestInstance(
				defaultToken, categories, xOCCIAtt, PluginHelper.CIRROS_IMAGE_TERM));

		Instance instance = occiComputeOpenStack.getInstance(defaultToken, FIRST_INSTANCE_ID);

		Assert.assertEquals(1, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(
				instance.toOCCIMessageFormatDetails(), OCCIComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(
				instance.toOCCIMessageFormatDetails(), OCCIComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(
				instance.toOCCIMessageFormatDetails(),
				OCCIComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(
				"server-" + FIRST_INSTANCE_ID,
				pluginHelper.getAttValueFromInstanceDetails(instance.toOCCIMessageFormatDetails(),
						OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}
	
	@Test
	public void testCreatePluginSpecifyingNetwork(){
		Properties properties = new Properties();
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_URL_KEY, PluginHelper.COMPUTE_OCCI_URL);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY, OCCIComputeApplication.INSTANCE_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY, OCCIComputeApplication.OS_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY, OCCIComputeApplication.RESOURCE_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY, OCCIComputeApplication.SMALL_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + PluginHelper.LINUX_X86_TERM, PluginHelper.CIRROS_IMAGE_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_NETWORK_KEY, "net1");

		occiComputeOpenStack = new OpenStackOCCIComputePlugin(properties);
		occiComputeOpenStack.setFlavors(flavors);
		
		List<Category> categories = new ArrayList<Category>();

		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		Assert.assertEquals(FIRST_INSTANCE_ID, occiComputeOpenStack.requestInstance(
				defaultToken, categories, xOCCIAtt, PluginHelper.CIRROS_IMAGE_TERM));

		Instance instance = occiComputeOpenStack.getInstance(defaultToken, FIRST_INSTANCE_ID);

		Assert.assertEquals(1, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(
				instance.toOCCIMessageFormatDetails(), OCCIComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(
				instance.toOCCIMessageFormatDetails(), OCCIComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(
				instance.toOCCIMessageFormatDetails(),
				OCCIComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertTrue(instance.toOCCIMessageFormatDetails().contains(
				OCCIHeaders.LINK + ": </network/net1"));
		Assert.assertEquals(
				"server-" + FIRST_INSTANCE_ID,
				pluginHelper.getAttValueFromInstanceDetails(instance.toOCCIMessageFormatDetails(),
						OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}
	
	@Test
	public void testCreatePluginNotSpecifyingNetwork(){		
		List<Category> categories = new ArrayList<Category>();
		
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);

		Assert.assertEquals(FIRST_INSTANCE_ID, occiComputeOpenStack.requestInstance(defaultToken,
				categories, xOCCIAtt, PluginHelper.CIRROS_IMAGE_TERM));

		Instance instance = occiComputeOpenStack.getInstance(defaultToken, FIRST_INSTANCE_ID);

		Assert.assertEquals(1, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(
				instance.toOCCIMessageFormatDetails(), OCCIComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(
				instance.toOCCIMessageFormatDetails(), OCCIComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(
				instance.toOCCIMessageFormatDetails(),
				OCCIComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertTrue(instance.toOCCIMessageFormatDetails().contains(
				OCCIHeaders.LINK + ": </network/default"));
		Assert.assertEquals(
				"server-" + FIRST_INSTANCE_ID,
				pluginHelper.getAttValueFromInstanceDetails(instance.toOCCIMessageFormatDetails(),
						OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}
	
	@Test(expected = OCCIException.class)
	public void testRequestWithoutOSCateory() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
		occiComputeOpenStack.requestInstance(defaultToken, categories,
				new HashMap<String, String>(), null);
	}

	@Test
	public void testRequestWithoutFlavorCategory() {
		List<Category> categories = new ArrayList<Category>();
		
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		Assert.assertEquals(FIRST_INSTANCE_ID, occiComputeOpenStack.requestInstance(
				defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM));
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCICoreAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OrderConstants.TEMPLATE_OS_SCHEME, OrderConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIComputeApplication.CORE_ATTRIBUTE_OCCI, "3");
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);

		occiComputeOpenStack.requestInstance(defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCIMemAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OrderConstants.TEMPLATE_OS_SCHEME, OrderConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIComputeApplication.MEMORY_ATTRIBUTE_OCCI, "5");
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);

		occiComputeOpenStack.requestInstance(defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCIArchAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OrderConstants.TEMPLATE_OS_SCHEME, OrderConstants.MIXIN_CLASS));
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI, "x86");
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);

		occiComputeOpenStack.requestInstance(defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCISpeedAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OrderConstants.TEMPLATE_OS_SCHEME, OrderConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIComputeApplication.SPEED_ATTRIBUTE_OCCI, "2");
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		occiComputeOpenStack.requestInstance(defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM);
	}

	@Test
	public void testSupportedOCCIAtt() {
		List<Category> categories = new ArrayList<Category>();

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI, "server-test");
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);

		Assert.assertEquals(FIRST_INSTANCE_ID,
				occiComputeOpenStack.requestInstance(defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM));

		String instanceDetails = occiComputeOpenStack.getInstance(defaultToken,
				FIRST_INSTANCE_ID).toOCCIMessageFormatDetails();
		Assert.assertEquals("server-test",
				pluginHelper.getAttValueFromInstanceDetails(instanceDetails, OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}

	@Test
	public void testMoreSupportedOCCIAtts() {
		List<Category> categories = new ArrayList<Category>();

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI, "server-test");
		xOCCIAtt.put("occi.compute.state", "inactive");
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);

		Assert.assertEquals(FIRST_INSTANCE_ID,
				occiComputeOpenStack.requestInstance(defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM));

		String instanceDetails = occiComputeOpenStack.getInstance(defaultToken,
				FIRST_INSTANCE_ID).toOCCIMessageFormatDetails();
		Assert.assertEquals("server-test",
				pluginHelper.getAttValueFromInstanceDetails(instanceDetails, OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
		Assert.assertEquals("inactive",
				pluginHelper.getAttValueFromInstanceDetails(instanceDetails, "occi.compute.state"));
	}

	@Test
	public void testGetAllInstanceIds() {
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());

		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		// requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		Assert.assertEquals(FIRST_INSTANCE_ID, occiComputeOpenStack.requestInstance(
				defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM));

		// check getting all instance ids
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(1, instanceLocations.size());
		Assert.assertEquals(PluginHelper.COMPUTE_OCCI_URL + OCCIComputeApplication.COMPUTE_TARGET + FIRST_INSTANCE_ID,
				instanceLocations.get(0));
	}

	@Test
	public void testGetAllManyInstanceIds() {
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());

		// requesting default instance
		List<Category> categories = new ArrayList<Category>();

		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId, occiComputeOpenStack.requestInstance(
					defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM));
		}

		// check getting all instance ids
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(expectedInstanceIds.size(), instanceLocations.size());
		for (String expectedId : expectedInstanceIds) {
			Assert.assertTrue(instanceLocations.contains(PluginHelper.COMPUTE_OCCI_URL + OCCIComputeApplication.COMPUTE_TARGET + expectedId));
		}
	}

	private List<String> getInstanceLocations(List<Instance> intances) {
		List<String> locations = new ArrayList<String>();
		for (Instance instance : intances) {
			// String instanceMessage = instance.toOCCIMassageFormatLocation();
			String[] lineTokens = instance.toOCCIMessageFormatLocation().split("Location:");
			locations.add(lineTokens[1].trim());
		}
		return locations;
	}

	@Test
	public void testGetInstanceDetails() {
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());

		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		// requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		Assert.assertEquals(FIRST_INSTANCE_ID, occiComputeOpenStack.requestInstance(
				defaultToken, categories, xOCCIAtt, PluginHelper.CIRROS_IMAGE_TERM));

		// check instance details
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(1, instanceLocations.size());
		String instanceDetails = occiComputeOpenStack.getInstance(defaultToken,
				FIRST_INSTANCE_ID).toOCCIMessageFormatDetails();
		Assert.assertEquals(FIRST_INSTANCE_ID,
				pluginHelper.getAttValueFromInstanceDetails(instanceDetails, OCCIComputeApplication.ID_CORE_ATTRIBUTE_OCCI));
		Assert.assertEquals(1, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(instanceDetails,
				OCCIComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(instanceDetails,
				OCCIComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(instanceDetails,
				OCCIComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertEquals("server-" + FIRST_INSTANCE_ID,
				pluginHelper.getAttValueFromInstanceDetails(instanceDetails, OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}

	@Test
	public void testDeleteAllInstancesEmpty() {
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());

		occiComputeOpenStack.removeInstances(defaultToken);
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());
	}

	@Test
	public void testDeleteAllManyInstances() {
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());

		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId, occiComputeOpenStack.requestInstance(
					defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM));
		}

		// check number of instances
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(expectedInstanceIds.size(), instanceLocations.size());

		// removing all instances
		occiComputeOpenStack.removeInstances(defaultToken);
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());
	}

	@Test
	public void testDeleteOneInstance() {
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());

		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId, occiComputeOpenStack.requestInstance(
					defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM));
		}

		// check number of instances
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(expectedInstanceIds.size(), instanceLocations.size());

		// removing one instances
		occiComputeOpenStack.removeInstance(defaultToken, FIRST_INSTANCE_ID);
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(expectedInstanceIds.size() - 1, instanceLocations.size());
	}
	
	@Test
	public void testBypassGetInstances() throws HttpException, IOException, URISyntaxException{		
		Request request = new Request(Method.GET, OCCITestHelper.URI_FOGBOW_COMPUTE);
		request.setResourceRef(OCCITestHelper.URI_FOGBOW_COMPUTE);
		Series<Header> requestHeaders = getRequestHeaders(request);
		requestHeaders.add(new Header(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN), PluginHelper.ACCESS_ID));
		Response response = new Response(request);		
		occiComputeOpenStack.bypass(request, response);
		
		// checking response
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatus().getCode());
		Assert.assertTrue(response.getEntity().getMediaType().toString().startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals("\n", response.getEntity().getText());
	}
		
	@Test
	public void testBypassGetSpecificInstance() throws HttpException, IOException, URISyntaxException{		
		//checking if there aren't instances
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());
		
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		// requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		Assert.assertEquals(FIRST_INSTANCE_ID, occiComputeOpenStack.requestInstance(
				defaultToken, categories, xOCCIAtt, PluginHelper.CIRROS_IMAGE_TERM));

		// checking if there is one instance		
		Request request = new Request(Method.GET, OCCITestHelper.URI_FOGBOW_COMPUTE);
		request.setResourceRef(OCCITestHelper.URI_FOGBOW_COMPUTE);
		Series<Header> requestHeaders = getRequestHeaders(request);
		requestHeaders.add(new Header(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN), PluginHelper.ACCESS_ID));		
		Response response = new Response(request);		
		occiComputeOpenStack.bypass(request, response);
		
		// checking response
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatus().getCode());
		Assert.assertTrue(response.getEntity().getMediaType().toString()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(HeaderUtils.X_OCCI_LOCATION_PREFIX + PluginHelper.COMPUTE_OCCI_URL
				+ OpenStackOCCIComputePlugin.COMPUTE_ENDPOINT + FIRST_INSTANCE_ID, response.getEntity()
				.getText());
		
		// getting instance details
		request = new Request(Method.GET, OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		request.setResourceRef(OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		requestHeaders = getRequestHeaders(request);
		requestHeaders.add(new Header(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN), PluginHelper.ACCESS_ID));		
		response = new Response(request);		
		occiComputeOpenStack.bypass(request, response);
		
		// checking instance details
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatus().getCode());
		Assert.assertTrue(response.getEntity().getMediaType().toString().startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));

		String instanceDetails = response.getEntity().getText();
		Assert.assertEquals(FIRST_INSTANCE_ID,
				pluginHelper.getAttValueFromInstanceDetails(instanceDetails, OCCIComputeApplication.ID_CORE_ATTRIBUTE_OCCI));
		Assert.assertEquals(1, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(instanceDetails,
				OCCIComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(instanceDetails,
				OCCIComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(instanceDetails,
				OCCIComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertEquals("server-" + FIRST_INSTANCE_ID,
				pluginHelper.getAttValueFromInstanceDetails(instanceDetails, OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}
	
	@Test
	public void testBypassDeleteOneInstance() throws URISyntaxException, ParseException, IOException {
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());

		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId, occiComputeOpenStack.requestInstance(
					defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM));
		}

		// check number of instances
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(expectedInstanceIds.size(), instanceLocations.size());

		// removing one instance
		Request request = new Request(Method.DELETE, OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		request.setResourceRef(OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		Series<Header> requestHeaders = getRequestHeaders(request);
		requestHeaders.add(new Header(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN), PluginHelper.ACCESS_ID));		
		Response response = new Response(request);		
		occiComputeOpenStack.bypass(request, response);
		
		// checking response
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatus().getCode());
		Assert.assertTrue(response.getEntity().getMediaType().toString().startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		String message = response.getEntity().getText();
		Assert.assertEquals(ResponseConstants.OK, message);
				
		// check number of instances
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(expectedInstanceIds.size() - 1, instanceLocations.size());
	}
	
	@Test
	public void testBypassDeleteAllManyInstances() throws URISyntaxException, ParseException, IOException {
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());

		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId, occiComputeOpenStack.requestInstance(
					defaultToken, categories, xOCCIAtt, PluginHelper.LINUX_X86_TERM));
		}

		// check number of instances
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(expectedInstanceIds.size(), instanceLocations.size());
		
		// removing all instances		
		Request request = new Request(Method.DELETE, OCCITestHelper.URI_FOGBOW_COMPUTE);
		request.setResourceRef(OCCITestHelper.URI_FOGBOW_COMPUTE);
		Series<Header> requestHeaders = getRequestHeaders(request);
		requestHeaders.add(new Header(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN), PluginHelper.ACCESS_ID));		
		Response response = new Response(request);		
		occiComputeOpenStack.bypass(request, response);
		
		// checking response
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatus().getCode());
		Assert.assertTrue(response.getEntity().getMediaType().toString().startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		String message = response.getEntity().getText();
		Assert.assertEquals(ResponseConstants.OK, message);
		
		// check number of instances
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testBypassPostInstance() throws URISyntaxException, ParseException, IOException {
		List<String> instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(0, instanceLocations.size());

		// requesting instance
		Request request = new Request(Method.POST, OCCITestHelper.URI_FOGBOW_COMPUTE);
		request.setResourceRef(OCCITestHelper.URI_FOGBOW_COMPUTE);
		Series<Header> requestHeaders = getRequestHeaders(request);
		requestHeaders.add(new Header(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN), PluginHelper.ACCESS_ID));
		requestHeaders.add(new Header(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE), OCCIHeaders.OCCI_CONTENT_TYPE));
		requestHeaders.add(new Header(HeaderUtils.normalize(OCCIHeaders.CATEGORY), new Category(PluginHelper.LINUX_X86_TERM,
				OCCIComputeApplication.OS_SCHEME, OrderConstants.MIXIN_CLASS).toHeader()));
		Response response = new Response(request);		
		occiComputeOpenStack.bypass(request, response);
		
		//checking response
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatus().getCode());
		Series<Header> responseHeaders = (Series<Header>) response.getAttributes().get(RESTLET_HEADERS_ATT_KEY);
		Assert.assertNotNull(requestHeaders);
		Assert.assertEquals(PluginHelper.COMPUTE_OCCI_URL + OpenStackOCCIComputePlugin.COMPUTE_ENDPOINT
				+ FIRST_INSTANCE_ID, responseHeaders.getFirstValue(HeaderUtils.normalize("Location")));		
		Assert.assertTrue(response.getEntity().getMediaType().toString().startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		String message = response.getEntity().getText();
		Assert.assertEquals(ResponseConstants.OK, message);
		
		// check number of instances
		instanceLocations = getInstanceLocations(occiComputeOpenStack
				.getInstances(defaultToken));
		Assert.assertEquals(1, instanceLocations.size());
	}
	
	@Test
	public void testConvertRequestToOcci() {
		Request request = new Request();
		request.setEntity("Hello\n" 
								+ OCCIHeaders.CATEGORY +": X \n " 
								+ OCCIHeaders.X_OCCI_ATTRIBUTE + ":Y" , MediaType.TEXT_PLAIN);
		Series<org.restlet.data.Header> requestHeaders = new Series<org.restlet.data.Header>(
				org.restlet.data.Header.class);
		String valueOne = "One";
		String valueTwo = "Two";
		
		requestHeaders.add(OCCIHeaders.CATEGORY, valueOne);
		requestHeaders.add(OCCIHeaders.CATEGORY, valueTwo);
		requestHeaders.add(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE, "");
		requestHeaders.add("Content-type", "x");
		
		Assert.assertEquals(4, requestHeaders.size());
		occiComputeOpenStack.convertRequestToOcci(request, requestHeaders);
		Assert.assertEquals(6, requestHeaders.size());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Series<Header> getRequestHeaders(Request request) {
		Series<Header> requestHeaders = (Series<Header>) request.getAttributes().get(
				RESTLET_HEADERS_ATT_KEY);
		if (requestHeaders == null) {
			requestHeaders = new Series(Header.class);
			request.getAttributes().put(RESTLET_HEADERS_ATT_KEY, requestHeaders);
		}
		return requestHeaders;
	}	
}
