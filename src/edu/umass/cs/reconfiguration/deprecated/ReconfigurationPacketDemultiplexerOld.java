/* Copyright (c) 2015 University of Massachusetts
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Initial developer(s): V. Arun */
package edu.umass.cs.reconfiguration.deprecated;

import java.nio.ByteBuffer;
import java.util.logging.Level;

import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.gigapaxos.PaxosConfig.PC;
import edu.umass.cs.nio.AbstractJSONPacketDemultiplexer;
import edu.umass.cs.nio.JSONMessenger;
import edu.umass.cs.nio.JSONPacket;
import edu.umass.cs.nio.nioutils.NIOHeader;
import edu.umass.cs.reconfiguration.reconfigurationpackets.ReconfigurationPacket;
import edu.umass.cs.reconfiguration.reconfigurationpackets.ReconfigurationPacket.PacketType;
import edu.umass.cs.utils.Config;
import edu.umass.cs.utils.DelayProfiler;
import edu.umass.cs.utils.Util;

/**
 * @author V. Arun
 */
public class ReconfigurationPacketDemultiplexerOld extends
		AbstractJSONPacketDemultiplexer {
	/**
	 * 
	 */
	public ReconfigurationPacketDemultiplexerOld() {
	}

	/**
	 * @param numThreads
	 */
	public ReconfigurationPacketDemultiplexerOld(int numThreads) {
		super(numThreads);
	}

	public ReconfigurationPacketDemultiplexerOld setThreadName(String name) {
		super.setThreadName(name);
		return this;
	}

	@Override
	public boolean handleMessage(JSONObject json, edu.umass.cs.nio.nioutils.NIOHeader header) {
		throw new RuntimeException(
				"This method should never be called unless we have \"forgotten\" to register or handle some packet types.");
	}
	
	private static final boolean BYTEIFICATION = Config.getGlobalBoolean(PC.BYTEIFICATION);

	@Override
	public JSONObject processHeader(byte[] msg, NIOHeader header) {
		long t=System.nanoTime();
		int type;
		JSONObject json = null;
		log.log(Level.FINEST, "{0} processHeader received message with header {1}", new Object[]{this, header});
		if (msg.length >= Integer.BYTES
				&& ReconfigurationPacket.PacketType.intToType
						.containsKey(type = ByteBuffer.wrap(msg, 0, 4).getInt())
				&& JSONPacket.couldBeJSON(msg, Integer.BYTES)
				&& type != PacketType.REPLICABLE_CLIENT_REQUEST.getInt()) {
			json = super.processHeader(msg, Integer.BYTES, header, true);
			if (json != null) {
				if (Util.oneIn(50))
					DelayProfiler.updateDelayNano("processHeader", t);
				return json;
			}
		} 
		else if (!BYTEIFICATION && JSONPacket.couldBeJSON(msg)
				&& (json = super.processHeader(msg, header, true)) != null) {
				return json;
		}
		// else prefix msg with addresses
		byte[] stamped = new byte[NIOHeader.BYTES + msg.length];
		ByteBuffer bbuf = ByteBuffer.wrap(stamped);
		bbuf.put(header.toBytes());
		bbuf.put(msg);
		if (Util.oneIn(50))
			DelayProfiler.updateDelayNano("processHeader", t);
		return new JSONMessenger.JSONObjectWrapper(stamped);
	}

	@Override
	public Integer getPacketType(JSONObject json) {
		if (json instanceof JSONMessenger.JSONObjectWrapper) {
			byte[] bytes = (byte[]) ((JSONMessenger.JSONObjectWrapper) json).getObj();
			if(!JSONPacket.couldBeJSON(bytes, NIOHeader.BYTES)) {
				// first 4 bytes (after 12 bytes of address) must be the type
				return ByteBuffer.wrap(bytes, NIOHeader.BYTES, Integer.BYTES)
						.getInt();
			}
			else {
				// return any valid type (assuming no more chained demultiplexers)
				return PacketType.ECHO_REQUEST.getInt();
			}
		} else
			try {
//				assert(ReconfigurationPacket.isReconfigurationPacket(json)) : json;
				return JSONPacket.getPacketType(json);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		return null;
	}
}
