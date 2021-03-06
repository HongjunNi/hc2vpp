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

package io.fd.hc2vpp.l3.utils.ip.write;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.fd.hc2vpp.common.translate.util.AddressTranslator;
import io.fd.hc2vpp.common.translate.util.ByteDataTranslator;
import io.fd.hc2vpp.common.translate.util.JvppReplyConsumer;
import io.fd.hc2vpp.common.translate.util.NamingContext;
import io.fd.honeycomb.translate.MappingContext;
import io.fd.honeycomb.translate.write.WriteFailedException;
import io.fd.vpp.jvpp.core.dto.IpNeighborAddDel;
import io.fd.vpp.jvpp.core.dto.SwInterfaceAddDelAddress;
import io.fd.vpp.jvpp.core.dto.SwInterfaceAddDelAddressReply;
import io.fd.vpp.jvpp.core.future.FutureJVppCore;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4AddressNoZone;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6AddressNoZone;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpp.vlan.rev170607.interfaces._interface.sub.interfaces.SubInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Utility class providing Ipv4/6 CUD support.
 */
public interface IpWriter extends ByteDataTranslator, AddressTranslator, JvppReplyConsumer {

    int DOTTED_QUAD_MASK_LENGTH = 4;
    int IPV4_ADDRESS_PART_BITS_COUNT = 8;
    int NETMASK_PART_LIMIT = 256; // 2 power to 8

    default void addDelAddress(@Nonnull final FutureJVppCore futureJVppCore, final boolean add,
                               final InstanceIdentifier<?> id,
                               @Nonnegative final int ifaceId,
                               @Nonnull final Ipv4AddressNoZone address, @Nonnegative final byte prefixLength)
            throws WriteFailedException {
        checkArgument(prefixLength > 0, "Invalid prefix length");
        checkNotNull(address, "address should not be null");

        final byte[] addressBytes = ipv4AddressNoZoneToArray(address);

        final CompletionStage<SwInterfaceAddDelAddressReply> swInterfaceAddDelAddressReplyCompletionStage =
                futureJVppCore.swInterfaceAddDelAddress(
                        getSwInterfaceAddDelAddressRequest(ifaceId, booleanToByte(add) /* isAdd */,
                                (byte) 0 /* isIpv6 */, (byte) 0 /* delAll */, prefixLength, addressBytes));

        getReplyForWrite(swInterfaceAddDelAddressReplyCompletionStage.toCompletableFuture(), id);
    }

    default void addDelAddress(@Nonnull final FutureJVppCore futureJVppCore, final boolean add,
                               final InstanceIdentifier<?> id,
                               @Nonnegative final int ifaceId,
                               @Nonnull final Ipv6AddressNoZone address, @Nonnegative final byte prefixLength)
            throws WriteFailedException {
        checkNotNull(address, "address should not be null");

        final byte[] addressBytes = ipv6AddressNoZoneToArray(address);

        final CompletionStage<SwInterfaceAddDelAddressReply> swInterfaceAddDelAddressReplyCompletionStage =
                futureJVppCore.swInterfaceAddDelAddress(
                        getSwInterfaceAddDelAddressRequest(ifaceId, booleanToByte(add) /* isAdd */,
                                (byte) 1 /* isIpv6 */, (byte) 0 /* delAll */, prefixLength, addressBytes));

        getReplyForWrite(swInterfaceAddDelAddressReplyCompletionStage.toCompletableFuture(), id);
    }

    default SwInterfaceAddDelAddress getSwInterfaceAddDelAddressRequest(final int swIfc, final byte isAdd,
                                                                        final byte ipv6, final byte deleteAll,
                                                                        final byte length, final byte[] addr) {
        final SwInterfaceAddDelAddress swInterfaceAddDelAddress = new SwInterfaceAddDelAddress();
        swInterfaceAddDelAddress.swIfIndex = swIfc;
        swInterfaceAddDelAddress.isAdd = isAdd;
        swInterfaceAddDelAddress.isIpv6 = ipv6;
        swInterfaceAddDelAddress.delAll = deleteAll;
        swInterfaceAddDelAddress.address = addr;
        swInterfaceAddDelAddress.addressLength = length;
        return swInterfaceAddDelAddress;
    }

    /**
     * Returns the prefix size in bits of the specified subnet mask. Example: For the subnet mask 255.255.255.128 it
     * returns 25 while for 255.0.0.0 it returns 8. If the passed subnetMask array is not complete or contains not only
     * leading ones, IllegalArgumentExpression is thrown
     *
     * @param mask the subnet mask in dot notation 255.255.255.255
     * @return the prefix length as number of bits
     */
    default byte getSubnetMaskLength(final String mask) {
        String[] maskParts = mask.split("\\.");

        checkArgument(maskParts.length == DOTTED_QUAD_MASK_LENGTH,
                "Network mask %s is not in Quad Dotted Decimal notation!", mask);

        long maskAsNumber = 0;
        for (int i = 0; i < DOTTED_QUAD_MASK_LENGTH; i++) {
            maskAsNumber <<= IPV4_ADDRESS_PART_BITS_COUNT;
            int value = Integer.parseInt(maskParts[i]);
            checkArgument(value < NETMASK_PART_LIMIT, "Network mask %s contains invalid number(s) over 255!", mask);
            checkArgument(value >= 0, "Network mask %s contains invalid negative number(s)!", mask);
            maskAsNumber += value;
        }

        String bits = Long.toBinaryString(maskAsNumber);
        checkArgument(bits.length() == IPV4_ADDRESS_PART_BITS_COUNT * DOTTED_QUAD_MASK_LENGTH,
                "Incorrect network mask %s", mask);
        final int leadingOnes = bits.indexOf('0');
        checkArgument(leadingOnes != -1, "Broadcast address %s is not allowed!", mask);
        checkArgument(bits.substring(leadingOnes).indexOf('1') == -1,
                "Non-contiguous network mask %s is not allowed!", mask);
        return (byte) leadingOnes;
    }

    default int subInterfaceIndex(final InstanceIdentifier<?> id, final NamingContext interfaceContext,
                                  final MappingContext mappingContext) {
        return interfaceContext
                .getIndex(id.firstKeyOf(Interface.class).getName() + "." + id.firstKeyOf(SubInterface.class).getIdentifier(),
                        mappingContext);
    }

    default void addDelNeighbour(@Nonnull final InstanceIdentifier<?> id,
                                 @Nonnull final Supplier<IpNeighborAddDel> requestSupplier,
                                 @Nonnull final FutureJVppCore api) throws WriteFailedException {
        getReplyForWrite(api.ipNeighborAddDel(requestSupplier.get()).toCompletableFuture(), id);
    }

    default IpNeighborAddDel preBindIpv4Request(final boolean add) {
        IpNeighborAddDel request = staticPreBindRequest(add);
        request.isIpv6 = 0;

        return request;
    }

    default IpNeighborAddDel preBindIpv6Request(final boolean add) {
        IpNeighborAddDel request = staticPreBindRequest(add);
        request.isIpv6 = 1;

        return request;
    }

    static IpNeighborAddDel staticPreBindRequest(final boolean add) {
        IpNeighborAddDel request = new IpNeighborAddDel();

        request.isAdd = ByteDataTranslator.INSTANCE.booleanToByte(add);
        request.isStatic = 1;
        return request;
    }
}
