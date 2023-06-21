package io.vertx.serviceresolver.srv;

import io.vertx.core.Vertx;
import io.vertx.serviceresolver.ServiceResolverTestBase;
import io.vertx.test.fakedns.FakeDNSServer;
import org.junit.Test;

public class SrvServiceResolverTest extends ServiceResolverTestBase {


  private Vertx vertx;
  private FakeDNSServer dnsServer;

  public void setUp() throws Exception {
    dnsServer = new FakeDNSServer();
    dnsServer.start();
  }

  public void tearDown() throws Exception {
    dnsServer.stop();
  }

  @Test
  public void testFoo() {
//    store(new RecordStore() {
//      @Override
//      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
//        Set<ResourceRecord> set = new HashSet<>();
//
//        ResourceRecordModifier rm = new ResourceRecordModifier();
//        rm.setDnsClass(RecordClass.IN);
//        rm.setDnsName("dns.vertx.io");
//        rm.setDnsTtl(100);
//        rm.setDnsType(RecordType.SRV);
//        rm.put(DnsAttribute.SERVICE_PRIORITY, String.valueOf(priority));
//        rm.put(DnsAttribute.SERVICE_WEIGHT, String.valueOf(weight));
//        rm.put(DnsAttribute.SERVICE_PORT, String.valueOf(port));
//        rm.put(DnsAttribute.DOMAIN_NAME, target);
//        set.add(rm.getEntry());
//        return set;
//      }
//    })

  }

}
