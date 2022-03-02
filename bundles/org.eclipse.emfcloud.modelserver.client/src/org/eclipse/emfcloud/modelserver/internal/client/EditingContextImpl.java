/********************************************************************************
 * Copyright (c) 2019-2022 EclipseSource and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0, or the MIT License which is
 * available at https://opensource.org/licenses/MIT.
 *
 * SPDX-License-Identifier: EPL-2.0 OR MIT
 ********************************************************************************/

package org.eclipse.emfcloud.modelserver.internal.client;

import java.util.function.BiFunction;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emfcloud.modelserver.client.EditingContext;
import org.eclipse.emfcloud.modelserver.command.CCommand;
import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;
import org.eclipse.emfcloud.modelserver.edit.CommandCodec;
import org.eclipse.emfcloud.modelserver.edit.EMFCommandCodec;

import com.fasterxml.jackson.databind.JsonNode;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Implementation of the client's editing context.
 */
public class EditingContextImpl<CLIENT> extends WebSocketListener implements EditingContext {

   private final CLIENT owner;
   private final BiFunction<? super CLIENT, ? super CCommand, String> encoder;
   private final CommandCodec codec = new EMFCommandCodec();
   private WebSocket socket;
   private int referenceCount = 1;

   /**
    * Initializes me.
    *
    * @param owner ModelServerClient which owns this EditingContext.
    */
   public EditingContextImpl(final CLIENT owner, final BiFunction<? super CLIENT, ? super CCommand, String> encoder) {
      super();

      this.owner = owner;
      this.encoder = encoder;
   }

   @Override
   public boolean execute(final Command command) throws EncodingException {
      CCommand serializable = codec.serverToClient(command);
      String message = encoder.apply(owner, serializable);
      return execute(message);
   }

   @Override
   public boolean execute(final String command) {
      if (socket == null) {
         return false;
      }

      return send(command);
   }

   protected final boolean send(final JsonNode json) {
      return send(json.toString());
   }

   protected final boolean send(final String jsonData) {
      // Wrap the data in a message
      String message = String.format("{data:%s}", jsonData);
      return socket.send(message);
   }

   //
   // WebSocket events
   //

   @Override
   public final void onOpen(final WebSocket webSocket, final Response response) {
      this.socket = webSocket;
   }

   @Override
   public final void onClosed(final WebSocket webSocket, final int code, final String reason) {
      if (webSocket == this.socket) {
         handleClosed(socket, code, reason);
      }
   }

   protected void handleClosed(final WebSocket webSocket, final int code, final String reason) {
      this.socket = null;
   }

   //
   // Reference counting
   //

   public void retain() {
      referenceCount = referenceCount + 1;
   }

   public boolean release() {
      referenceCount = referenceCount - 1;
      return referenceCount <= 0;
   }

}
