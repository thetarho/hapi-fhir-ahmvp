package ca.uhn.fhir.rest.server;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.InstantDt;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.test.utilities.HttpClientExtension;
import ca.uhn.fhir.test.utilities.JettyUtil;
import ca.uhn.fhir.test.utilities.server.RestfulServerExtension;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.hl7.fhir.dstu2.model.Bundle;
import org.hl7.fhir.dstu2.model.Patient;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchHl7OrgDstu2Test {

  private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SearchHl7OrgDstu2Test.class);
  private static final FhirContext ourCtx = FhirContext.forDstu2Hl7OrgCached();
  private static InstantDt ourReturnPublished;

  @RegisterExtension
  public static RestfulServerExtension ourServer = new RestfulServerExtension(ourCtx)
      .registerProvider(new DummyPatientResourceProvider())
      .withPagingProvider(new FifoMemoryPagingProvider(100))
      .setDefaultResponseEncoding(EncodingEnum.XML)
      .setDefaultPrettyPrint(false);

  @RegisterExtension
  public static HttpClientExtension ourClient = new HttpClientExtension();

  @Test
  public void testEncodeConvertsReferencesToRelative() throws Exception {
    HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=searchWithRef");
    HttpResponse status = ourClient.execute(httpGet);
    String responseContent = IOUtils.toString(status.getEntity().getContent());
    IOUtils.closeQuietly(status.getEntity().getContent());
    ourLog.info(responseContent);

    assertThat(responseContent, not(containsString("text")));

    assertEquals(200, status.getStatusLine().getStatusCode());
    Patient patient = (Patient) ourCtx.newXmlParser().parseResource(Bundle.class, responseContent).getEntry().get(0).getResource();
    String ref = patient.getManagingOrganization().getReference();
    assertEquals("Organization/555", ref);
    assertNull(status.getFirstHeader(Constants.HEADER_CONTENT_LOCATION));
  }

  @Test
  public void testEncodeConvertsReferencesToRelativeJson() throws Exception {
    HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=searchWithRef&_format=json");
    HttpResponse status = ourClient.execute(httpGet);
    String responseContent = IOUtils.toString(status.getEntity().getContent());
    IOUtils.closeQuietly(status.getEntity().getContent());
    ourLog.info(responseContent);

    assertThat(responseContent, not(containsString("text")));

    assertEquals(200, status.getStatusLine().getStatusCode());
    Patient patient = (Patient) ourCtx.newJsonParser().parseResource(Bundle.class, responseContent).getEntry().get(0).getResource();
    String ref = patient.getManagingOrganization().getReference();
    assertEquals("Organization/555", ref);
    assertNull(status.getFirstHeader(Constants.HEADER_CONTENT_LOCATION));
  }

  @Test
  public void testResultBundleHasUuid() throws Exception {
    HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=searchWithRef");
    HttpResponse status = ourClient.execute(httpGet);
    String responseContent = IOUtils.toString(status.getEntity().getContent());
    IOUtils.closeQuietly(status.getEntity().getContent());
    ourLog.info(responseContent);

    assertEquals(200, status.getStatusLine().getStatusCode());
    assertThat(responseContent, matchesPattern(".*id value..[0-9a-f-]+\\\".*"));
  }

  @Test
  public void testResultBundleHasUpdateTime() throws Exception {
    ourReturnPublished = new InstantDt("2011-02-03T11:22:33Z");
    assertEquals(ourReturnPublished.getValueAsString(), "2011-02-03T11:22:33Z");

    HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=searchWithBundleProvider&_pretty=true");
    HttpResponse status = ourClient.execute(httpGet);
    String responseContent = IOUtils.toString(status.getEntity().getContent());
    IOUtils.closeQuietly(status.getEntity().getContent());
    ourLog.info(responseContent);

    assertThat(responseContent, stringContainsInOrder("<lastUpdated value=\"2011-02-03T11:22:33Z\"/>"));
  }

  /**
   * Created by dsotnikov on 2/25/2014.
   */
  public static class DummyPatientResourceProvider implements IResourceProvider {


    @Override
    public Class<Patient> getResourceType() {
      return Patient.class;
    }

    @Search(queryName = "searchWithBundleProvider")
    public IBundleProvider searchWithBundleProvider() {
      return new IBundleProvider() {

        @Override
        public InstantDt getPublished() {
          return ourReturnPublished;
        }

        @Nonnull
        @Override
        public List<IBaseResource> getResources(int theFromIndex, int theToIndex) {
          throw new IllegalStateException();
        }

        @Override
        public Integer preferredPageSize() {
          return null;
        }

        @Override
        public Integer size() {
          return 0;
        }

        @Override
        public String getUuid() {
          return null;
        }
      };
    }

    @Search(queryName = "searchWithRef")
    public Patient searchWithRef() {
      Patient patient = new Patient();
      patient.setId("Patient/1/_history/1");
      patient.getManagingOrganization().setReference(ourServer.getBaseUrl() + "/Organization/555/_history/666");
      return patient;
    }

  }

  @AfterAll
  public static void afterClass() throws Exception {
    TestUtil.randomizeLocaleAndTimezone();
  }

}
