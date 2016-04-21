/*
 * Copyright (c) 2016 Cisco and/or its affiliates.
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

package io.fd.honeycomb.v3po.translate.v3po.vppstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import io.fd.honeycomb.v3po.translate.impl.read.CompositeListReader;
import io.fd.honeycomb.v3po.translate.impl.read.CompositeRootReader;
import io.fd.honeycomb.v3po.translate.read.ReadContext;
import io.fd.honeycomb.v3po.translate.read.Reader;
import io.fd.honeycomb.v3po.translate.util.read.DelegatingReaderRegistry;
import io.fd.honeycomb.v3po.translate.v3po.util.NamingContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.VppState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.VppStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.state.BridgeDomains;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.state.Version;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.state.VersionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.state.bridge.domains.BridgeDomain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.state.bridge.domains.BridgeDomainBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.state.bridge.domains.BridgeDomainKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.state.bridge.domains.bridge.domain.L2Fib;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.v3po.rev150105.vpp.state.bridge.domains.bridge.domain.L2FibKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.openvpp.jvpp.dto.BridgeDomainDetails;
import org.openvpp.jvpp.dto.BridgeDomainDetailsReplyDump;
import org.openvpp.jvpp.dto.BridgeDomainDump;
import org.openvpp.jvpp.dto.L2FibTableDump;
import org.openvpp.jvpp.dto.L2FibTableEntry;
import org.openvpp.jvpp.dto.L2FibTableEntryReplyDump;
import org.openvpp.jvpp.dto.ShowVersion;
import org.openvpp.jvpp.dto.ShowVersionReply;
import org.openvpp.jvpp.future.FutureJVpp;

public class VppStateTest {

    @Mock
    private FutureJVpp api;
    @Mock
    private ReadContext ctx;

    private NamingContext bdContext;
    private NamingContext interfaceContext;

    private CompositeRootReader<VppState, VppStateBuilder> vppStateReader;
    private DelegatingReaderRegistry readerRegistry;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        bdContext = new NamingContext("generatedBdName");
        interfaceContext = new NamingContext("generatedInterfaceName");
        vppStateReader = VppStateTestUtils.getVppStateReader(api, bdContext, interfaceContext);
        readerRegistry = new DelegatingReaderRegistry(Collections.<Reader<? extends DataObject>>singletonList(vppStateReader));
    }

    private static Version getVersion() {
        return new VersionBuilder()
                .setName("test")
                .setBuildDirectory("1")
                .setBranch("2")
                .setBuildDate("3")
                .build();
    }

    private void whenShowVersionThenReturn(int retval, Version version) throws ExecutionException, InterruptedException {
        final CompletionStage<ShowVersionReply> replyCS = mock(CompletionStage.class);
        final CompletableFuture<ShowVersionReply> replyFuture = mock(CompletableFuture.class);
        when(replyCS.toCompletableFuture()).thenReturn(replyFuture);
        final ShowVersionReply reply = new ShowVersionReply();
        reply.retval = 0; // success
        reply.buildDate = version.getBuildDate().getBytes();
        reply.program = version.getName().getBytes();
        reply.version = version.getBranch().getBytes();
        reply.buildDirectory = version.getBuildDirectory().getBytes();
        when(replyFuture.get()).thenReturn(reply);
        when(api.showVersion(any(ShowVersion.class))).thenReturn(replyCS);
    }

    private void whenL2FibTableDumpThenReturn(final List<L2FibTableEntry> entryList) throws ExecutionException, InterruptedException {
        final CompletionStage<L2FibTableEntryReplyDump> replyCS = mock(CompletionStage.class);
        final CompletableFuture<L2FibTableEntryReplyDump> replyFuture = mock(CompletableFuture.class);
        when(replyCS.toCompletableFuture()).thenReturn(replyFuture);
        final L2FibTableEntryReplyDump reply = new L2FibTableEntryReplyDump();
        reply.l2FibTableEntry = entryList;
        when(replyFuture.get()).thenReturn(reply);
        when(api.l2FibTableDump(any(L2FibTableDump.class))).thenReturn(replyCS);
    }

    private void whenBridgeDomainDumpThenReturn(final List<BridgeDomainDetails> bdList) throws ExecutionException, InterruptedException {
        final CompletionStage<BridgeDomainDetailsReplyDump> replyCS = mock(CompletionStage.class);
        final CompletableFuture<BridgeDomainDetailsReplyDump> replyFuture = mock(CompletableFuture.class);
        when(replyCS.toCompletableFuture()).thenReturn(replyFuture);
        final BridgeDomainDetailsReplyDump reply = new BridgeDomainDetailsReplyDump();
        reply.bridgeDomainDetails = bdList;
        when(replyFuture.get()).thenReturn(reply);

        doAnswer(invocation -> {
            BridgeDomainDump request = (BridgeDomainDump)invocation.getArguments()[0];
            if (request.bdId == -1) {
                reply.bridgeDomainDetails = bdList;
            } else {
                reply.bridgeDomainDetails = Collections.singletonList(bdList.get(request.bdId));
            }
            return replyCS;
        }).when(api).bridgeDomainDump(any(BridgeDomainDump.class));
    }

    @Test
    public void testReadAll() throws Exception {
        final Version version = getVersion();
        whenShowVersionThenReturn(0, version);

        final List<BridgeDomainDetails> bdList = Arrays.asList(new BridgeDomainDetails(), new BridgeDomainDetails());
        whenBridgeDomainDumpThenReturn(bdList);

        final Multimap<InstanceIdentifier<? extends DataObject>, ? extends DataObject> dataObjects = readerRegistry.readAll(ctx);
        assertEquals(dataObjects.size(), 1);
        final VppState dataObject = (VppState)Iterables.getOnlyElement(dataObjects.get(Iterables.getOnlyElement(dataObjects.keySet())));
        assertEquals(version, dataObject.getVersion());
        assertEquals(2, dataObject.getBridgeDomains().getBridgeDomain().size());
    }

    @Test
    public void testReadSpecific() throws Exception {
        final Version version = getVersion();
        whenShowVersionThenReturn(0, version);
        whenBridgeDomainDumpThenReturn(Collections.emptyList());

        final Optional<? extends DataObject> read = readerRegistry.read(InstanceIdentifier.create(VppState.class), ctx);
        assertTrue(read.isPresent());
        assertEquals(version, ((VppState) read.get()).getVersion());
    }

    @Test
    public void testReadBridgeDomains() throws Exception {
        final Version version = getVersion();
        whenShowVersionThenReturn(0, version);
        whenBridgeDomainDumpThenReturn(Collections.singletonList(new BridgeDomainDetails()));

        VppState readRoot = (VppState) readerRegistry.read(InstanceIdentifier.create(VppState.class), ctx).get();

        Optional<? extends DataObject> read =
            readerRegistry.read(InstanceIdentifier.create(VppState.class).child(BridgeDomains.class), ctx);
        assertTrue(read.isPresent());
        assertEquals(readRoot.getBridgeDomains(), read.get());
    }

    /**
     * L2fib does not have a dedicated reader, relying on auto filtering
     */
    @Test
    public void testReadL2Fib() throws Exception {
        final BridgeDomainDetails bd = new BridgeDomainDetails();
        bd.bdId = 0;
        final String bdName = "bdn1";
        bdContext.addName(bd.bdId, bdName);
        whenBridgeDomainDumpThenReturn(Collections.singletonList(bd));
        final L2FibTableEntry l2FibEntry = new L2FibTableEntry();
        l2FibEntry.bdId = 0;
        l2FibEntry.mac = 0x0605040302010000L;
        whenL2FibTableDumpThenReturn(Collections.singletonList(l2FibEntry));

        // Deep child without a dedicated reader with specific l2fib key
        Optional<? extends DataObject> read =
            readerRegistry.read(InstanceIdentifier.create(VppState.class).child(BridgeDomains.class).child(
                BridgeDomain.class, new BridgeDomainKey("bdn1"))
                .child(L2Fib.class, new L2FibKey(new PhysAddress("01:02:03:04:05:06"))), ctx);
        assertTrue(read.isPresent());

        // non existing l2fib
        read =
            readerRegistry.read(InstanceIdentifier.create(VppState.class).child(BridgeDomains.class).child(
                BridgeDomain.class, new BridgeDomainKey("bdn1"))
                .child(L2Fib.class, new L2FibKey(new PhysAddress("FF:FF:FF:04:05:06"))), ctx);
        assertFalse(read.isPresent());
    }

    @Test
    public void testReadBridgeDomainAll() throws Exception {
        final Version version = getVersion();
        whenShowVersionThenReturn(0, version);
        whenBridgeDomainDumpThenReturn(Collections.singletonList(new BridgeDomainDetails()));

        VppState readRoot = (VppState) readerRegistry.read(InstanceIdentifier.create(VppState.class), ctx).get();

        final CompositeListReader<BridgeDomain, BridgeDomainKey, BridgeDomainBuilder> bridgeDomainReader =
            VppStateTestUtils.getBridgeDomainReader(api, bdContext, interfaceContext);

        final List<BridgeDomain> read =
            bridgeDomainReader.readList(InstanceIdentifier.create(VppState.class).child(BridgeDomains.class).child(
                BridgeDomain.class), ctx);

        assertEquals(readRoot.getBridgeDomains().getBridgeDomain(), read);
    }

    @Test
    public void testReadBridgeDomain() throws Exception {
        final BridgeDomainDetails bd = new BridgeDomainDetails();
        bd.bdId = 0;
        final String bdName = "bdn1";
        bdContext.addName(bd.bdId, bdName);
        whenBridgeDomainDumpThenReturn(Collections.singletonList(bd));
        whenShowVersionThenReturn(0, getVersion());

        VppState readRoot = (VppState) readerRegistry.read(InstanceIdentifier.create(VppState.class), ctx).get();

        final Optional<? extends DataObject> read =
            readerRegistry.read(InstanceIdentifier.create(VppState.class).child(BridgeDomains.class).child(
                BridgeDomain.class, new BridgeDomainKey(bdName)), ctx);

        assertTrue(read.isPresent());
        assertEquals(Iterables.find(readRoot.getBridgeDomains().getBridgeDomain(),
                input -> input.getKey().getName().equals(bdName)), read.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadBridgeDomainNotExisting() throws Exception {
        final Optional<? extends DataObject> read =
            readerRegistry.read(InstanceIdentifier.create(VppState.class).child(BridgeDomains.class).child(
                BridgeDomain.class, new BridgeDomainKey("NOT EXISTING")), ctx);
        assertFalse(read.isPresent());
    }

    @Test
    public void testReadVersion() throws Exception {
        whenShowVersionThenReturn(0, getVersion());
        whenBridgeDomainDumpThenReturn(Collections.emptyList());
        VppState readRoot = (VppState) readerRegistry.read(InstanceIdentifier.create(VppState.class), ctx).get();

        Optional<? extends DataObject> read =
            readerRegistry.read(InstanceIdentifier.create(VppState.class).child(Version.class), ctx);
        assertTrue(read.isPresent());
        assertEquals(readRoot.getVersion(), read.get());
    }
}