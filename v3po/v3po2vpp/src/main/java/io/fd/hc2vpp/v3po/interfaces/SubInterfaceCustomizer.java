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

package io.fd.hc2vpp.v3po.interfaces;

import static com.google.common.base.Preconditions.checkState;
import static io.fd.hc2vpp.v3po.util.SubInterfaceUtils.getNumberOfTags;
import static io.fd.hc2vpp.v3po.util.SubInterfaceUtils.getSubInterfaceName;

import com.google.common.base.Preconditions;
import io.fd.hc2vpp.common.translate.util.ByteDataTranslator;
import io.fd.hc2vpp.common.translate.util.FutureJVppCustomizer;
import io.fd.hc2vpp.common.translate.util.JvppReplyConsumer;
import io.fd.hc2vpp.common.translate.util.NamingContext;
import io.fd.honeycomb.translate.spi.write.ListWriterCustomizer;
import io.fd.honeycomb.translate.write.WriteContext;
import io.fd.honeycomb.translate.write.WriteFailedException;
import io.fd.vpp.jvpp.core.dto.CreateSubif;
import io.fd.vpp.jvpp.core.dto.CreateSubifReply;
import io.fd.vpp.jvpp.core.dto.SwInterfaceSetFlags;
import io.fd.vpp.jvpp.core.dto.SwInterfaceSetFlagsReply;
import io.fd.vpp.jvpp.core.future.FutureJVppCore;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.CVlan;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.Dot1qVlanId;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.SVlan;
import org.opendaylight.yang.gen.v1.urn.ieee.params.xml.ns.yang.dot1q.types.rev150626.dot1q.tag.or.any.Dot1qTag;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170607._802dot1ad;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170607.interfaces._interface.sub.interfaces.SubInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170607.interfaces._interface.sub.interfaces.SubInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170607.match.attributes.MatchType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170607.match.attributes.match.type.Default;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170607.match.attributes.match.type.VlanTagged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170607.sub._interface.base.attributes.tags.Tag;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writer Customizer responsible for sub interface creation.<br> Sends {@code create_subif} message to VPP.<br>
 * Equivalent of invoking {@code vppclt create subif} command.
 */
public class SubInterfaceCustomizer extends FutureJVppCustomizer
        implements ListWriterCustomizer<SubInterface, SubInterfaceKey>, ByteDataTranslator, JvppReplyConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SubInterfaceCustomizer.class);
    private final NamingContext interfaceContext;

    public SubInterfaceCustomizer(@Nonnull final FutureJVppCore futureJVppCore,
                                  @Nonnull final NamingContext interfaceContext) {
        super(futureJVppCore);
        this.interfaceContext = Preconditions.checkNotNull(interfaceContext, "interfaceContext should not be null");
    }

    @Override
    public void writeCurrentAttributes(@Nonnull final InstanceIdentifier<SubInterface> id,
                                       @Nonnull final SubInterface dataAfter, @Nonnull final WriteContext writeContext)
            throws WriteFailedException {
        final String superIfName = id.firstKeyOf(Interface.class).getName();
        createSubInterface(id, superIfName, dataAfter, writeContext);
    }

    private void createSubInterface(final InstanceIdentifier<SubInterface> id, @Nonnull final String superIfName,
                                    @Nonnull final SubInterface subInterface, final WriteContext writeContext)
            throws WriteFailedException {
        final int superIfIndex = interfaceContext.getIndex(superIfName, writeContext.getMappingContext());
        final CompletionStage<CreateSubifReply> createSubifReplyCompletionStage =
                getFutureJVpp().createSubif(getCreateSubifRequest(subInterface, superIfIndex));

        final CreateSubifReply reply =
                getReplyForCreate(createSubifReplyCompletionStage.toCompletableFuture(), id, subInterface);

        setInterfaceState(id, reply.swIfIndex, booleanToByte(subInterface.isEnabled()));
        interfaceContext.addName(reply.swIfIndex,
                getSubInterfaceName(superIfName, Math.toIntExact(subInterface.getIdentifier())),
                writeContext.getMappingContext());
        LOG.debug("Sub interface created successfully for: {}, subInterface: {}", superIfName, subInterface);
    }

    private CreateSubif getCreateSubifRequest(@Nonnull final SubInterface subInterface, final int swIfIndex) {
        // TODO HONEYCOMB-183 add validation
        CreateSubif request = new CreateSubif();
        request.subId = Math.toIntExact(subInterface.getIdentifier().intValue());
        request.swIfIndex = swIfIndex;

        final int numberOfTags = getNumberOfTags(subInterface.getTags());
        switch (numberOfTags) {
            case 0:
                request.noTags = 1;
                break;
            case 1:
                request.oneTag = 1;
                break;
            case 2:
                request.twoTags = 1;
                break;
        }
        request.dot1Ad = booleanToByte(_802dot1ad.class == subInterface.getVlanType());

        // TODO HONEYCOMB-183 match should be mandatory
        final MatchType matchType = subInterface.getMatch().getMatchType();
        request.exactMatch = booleanToByte(
            matchType instanceof VlanTagged
                && ((VlanTagged) matchType).getVlanTagged().isMatchExactTags()
        );
        request.defaultSub = booleanToByte(matchType instanceof Default);
        if (numberOfTags > 0) {
            for (final Tag tag : subInterface.getTags().getTag()) {
                if (tag.getIndex() == 0) {
                    setOuterTag(request, tag);
                } else if (tag.getIndex() == 1) {
                    setInnerTag(request, tag);
                }
            }
        }
        return request;
    }

    private void setOuterTag(final CreateSubif request, final Tag outerTag) {
        checkState(SVlan.class == outerTag.getDot1qTag().getTagType(), "Service Tag expected at index 0");
        final Dot1qTag.VlanId vlanId = outerTag.getDot1qTag().getVlanId();

        request.outerVlanId = dot1qVlanIdToShort(vlanId.getDot1qVlanId());
        request.outerVlanIdAny = booleanToByte(Dot1qTag.VlanId.Enumeration.Any.equals(vlanId.getEnumeration()));
    }

    private void setInnerTag(final CreateSubif request, final Tag innerTag) {
        checkState(CVlan.class == innerTag.getDot1qTag().getTagType(), "Customer Tag expected at index 1");
        final Dot1qTag.VlanId vlanId = innerTag.getDot1qTag().getVlanId();

        request.innerVlanId = dot1qVlanIdToShort(vlanId.getDot1qVlanId());
        request.innerVlanIdAny = booleanToByte(Dot1qTag.VlanId.Enumeration.Any.equals(vlanId.getEnumeration()));
    }

    private static short dot1qVlanIdToShort(@Nullable Dot1qVlanId dot1qVlanId) {
        if (dot1qVlanId == null) {
            return 0; // tell VPP that optional argument is missing
        } else {
            return dot1qVlanId.getValue().shortValue();
        }
    }

    @Override
    public void updateCurrentAttributes(@Nonnull final InstanceIdentifier<SubInterface> id,
                                        @Nonnull final SubInterface dataBefore, @Nonnull final SubInterface dataAfter,
                                        @Nonnull final WriteContext writeContext)
            throws WriteFailedException {
        final String subIfaceName = getSubInterfaceName(id.firstKeyOf(Interface.class).getName(),
                Math.toIntExact(dataAfter.getIdentifier()));
        setInterfaceState(id, interfaceContext.getIndex(subIfaceName, writeContext.getMappingContext()),
                booleanToByte(dataAfter.isEnabled()));
    }

    private void setInterfaceState(final InstanceIdentifier<SubInterface> id, final int swIfIndex, final byte enabled)
            throws WriteFailedException {
        final SwInterfaceSetFlags swInterfaceSetFlags = new SwInterfaceSetFlags();
        swInterfaceSetFlags.swIfIndex = swIfIndex;
        swInterfaceSetFlags.adminUpDown = enabled;

        final CompletionStage<SwInterfaceSetFlagsReply> swInterfaceSetFlagsReplyFuture =
                getFutureJVpp().swInterfaceSetFlags(swInterfaceSetFlags);

        LOG.debug("Updating interface state for interface if={}, enabled: {}", swIfIndex, enabled);

        SwInterfaceSetFlagsReply reply =
                getReplyForWrite(swInterfaceSetFlagsReplyFuture.toCompletableFuture(), id);
        LOG.debug("Interface state updated successfully for interface index: {}, enabled: {}, ctxId: {}",
                swIfIndex, enabled, reply.context);
    }

    @Override
    public void deleteCurrentAttributes(@Nonnull final InstanceIdentifier<SubInterface> id,
                                        @Nonnull final SubInterface dataBefore,
                                        @Nonnull final WriteContext writeContext)
            throws WriteFailedException.DeleteFailedException {
        throw new UnsupportedOperationException("Sub interface delete is not supported");
    }
}
