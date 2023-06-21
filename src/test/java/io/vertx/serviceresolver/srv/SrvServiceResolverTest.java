package io.vertx.serviceresolver.srv;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.ext.unit.TestContext;
import io.vertx.serviceresolver.ServiceAddress;
import io.vertx.serviceresolver.ServiceResolverTestBase;
import io.vertx.serviceresolver.impl.srv.SrvResolver;
import io.vertx.test.fakedns.FakeDNSServer;
import org.apache.directory.server.dns.messages.*;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.junit.Test;
import org.apache.directory.server.dns.store.RecordStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SrvServiceResolverTest extends ServiceResolverTestBase {

  private FakeDNSServer dnsServer;

  public void setUp() throws Exception {
    super.setUp();

    dnsServer = new FakeDNSServer();
    dnsServer.start();

    SrvResolver resolver = new SrvResolver((VertxInternal) vertx, FakeDNSServer.IP_ADDRESS, FakeDNSServer.PORT);

    client = (HttpClientInternal) vertx.createHttpClient();
    client.addressResolver(resolver);
  }

  public void tearDown() throws Exception {
    dnsServer.stop();
    super.tearDown();
  }

  @Test
  public void testResolve(TestContext should) throws Exception {
    startPods(2, req -> {
      req.response().end("" + req.localAddress().port());
    });
    dnsServer.store(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
        Set<ResourceRecord> set = new HashSet<>();
        if ("_http._tcp.example.com".equals(questionRecord.getDomainName())) {
          for (int i = 0;i < 2;i++) {
            ResourceRecordModifier rm = new ResourceRecordModifier();
            rm.setDnsClass(RecordClass.IN);
            rm.setDnsName("dns.vertx.io." + i);
            rm.setDnsTtl(100);
            rm.setDnsType(RecordType.SRV);
            rm.put(DnsAttribute.SERVICE_PRIORITY, String.valueOf(1));
            rm.put(DnsAttribute.SERVICE_WEIGHT, String.valueOf(1));
            rm.put(DnsAttribute.SERVICE_PORT, String.valueOf(8080 + i));
            rm.put(DnsAttribute.DOMAIN_NAME, "localhost");
            set.add(rm.getEntry());
          }
        }
        return set;
      }
    });
    Set<String> set = new HashSet<>(Arrays.asList("8080", "8081"));
    should.assertTrue(set.remove(get(ServiceAddress.create("_http._tcp.example.com.")).toString()));
    should.assertTrue(set.remove(get(ServiceAddress.create("_http._tcp.example.com.")).toString()));
    should.assertEquals(Collections.emptySet(), set);
  }
}
