/*
 * Copyright (c) 2017 Cisco and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fd.hc2vpp.l3.write;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fd.hc2vpp.common.test.write.WriterCustomizerTest;
import io.fd.hc2vpp.common.translate.util.ByteDataTranslator;
import io.fd.hc2vpp.l3.write.ipv4.ProxyRangeCustomizer;
import io.fd.honeycomb.translate.write.WriteFailedException;
import io.fd.vpp.jvpp.core.dto.ProxyArpAddDel;
import io.fd.vpp.jvpp.core.dto.ProxyArpAddDelReply;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4AddressNoZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.proxy.arp.rev170315.ProxyRanges;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.proxy.arp.rev170315.proxy.ranges.ProxyRange;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.proxy.arp.rev170315.proxy.ranges.ProxyRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.proxy.arp.rev170315.proxy.ranges.ProxyRangeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;

public class ProxyRangeCustomizerTest extends WriterCustomizerTest implements ByteDataTranslator {

    private KeyedInstanceIdentifier<ProxyRange, ProxyRangeKey> IID;
    private ProxyRange RANGE;
    private ProxyRangeCustomizer customizer;

    @Override
    public void setUpTest() throws Exception {
        final Ipv4Address highAddr = new Ipv4AddressNoZone("10.1.1.2");
        final Ipv4Address lowAddr = new Ipv4AddressNoZone("10.1.1.1");
        final long vrfId = 123;
        IID = InstanceIdentifier.create(ProxyRanges.class)
            .child(ProxyRange.class, new ProxyRangeKey(highAddr, lowAddr, vrfId));
        RANGE = new ProxyRangeBuilder().setVrfId(vrfId).setHighAddr(highAddr).setLowAddr(new Ipv4AddressNoZone(lowAddr))
            .build();
        customizer = new ProxyRangeCustomizer(api);
    }

    @Test
    public void testWrite() throws WriteFailedException {
        when(api.proxyArpAddDel(any())).thenReturn(future(new ProxyArpAddDelReply()));
        customizer.writeCurrentAttributes(IID, RANGE, writeContext);
        verify(api).proxyArpAddDel(expectedAddDelRequest(true));
    }

    @Test(expected = WriteFailedException.class)
    public void testWriteFailed() throws WriteFailedException {
        when(api.proxyArpAddDel(any())).thenReturn(failedFuture());
        customizer.writeCurrentAttributes(IID, RANGE, writeContext);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdate() throws WriteFailedException {
        customizer.updateCurrentAttributes(IID, RANGE, RANGE, writeContext);
    }

    @Test
    public void testDelete() throws WriteFailedException {
        when(api.proxyArpAddDel(any())).thenReturn(future(new ProxyArpAddDelReply()));
        customizer.deleteCurrentAttributes(IID, RANGE, writeContext);
        verify(api).proxyArpAddDel(expectedAddDelRequest(false));
    }

    @Test(expected = WriteFailedException.DeleteFailedException.class)
    public void testDeleteFailed() throws WriteFailedException {
        when(api.proxyArpAddDel(any())).thenReturn(failedFuture());
        customizer.deleteCurrentAttributes(IID, RANGE, writeContext);
    }

    private ProxyArpAddDel expectedAddDelRequest(final boolean isAdd) {
        final ProxyArpAddDel request = new ProxyArpAddDel();
        request.isAdd = booleanToByte(isAdd);
        request.vrfId = 123;
        request.lowAddress = new byte[] {10, 1, 1, 1};
        request.hiAddress = new byte[] {10, 1, 1, 2};
        return request;
    }
}
