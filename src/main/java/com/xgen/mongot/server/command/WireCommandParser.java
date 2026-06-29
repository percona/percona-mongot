package com.xgen.mongot.server.command;

import static com.xgen.mongot.util.Check.checkArg;

import com.xgen.mongot.server.message.InboundMessage;
import com.xgen.mongot.server.message.MessageMessage;
import com.xgen.mongot.server.message.MessageSection;
import com.xgen.mongot.server.message.MessageSectionBody;
import com.xgen.mongot.server.message.QueryMessage;
import java.util.NoSuchElementException;

/** This parser extracts command name and command args from OP_MSG or OP_QUERY. */
public class WireCommandParser {

  public static ParsedCommand parse(MessageMessage opMsg) {
    checkArg(!opMsg.sections().isEmpty(), "OP_MSG must have at least one body section");
    MessageSection section = opMsg.sections().get(0);
    checkArg(
        section instanceof MessageSectionBody, "OP_MSG must have first section as a body section");
    MessageSectionBody body = (MessageSectionBody) section;
    try {
      String commandName = body.body.getFirstKey();
      return new ParsedCommand(commandName, body.body);
    } catch (NoSuchElementException ignored) {
      throw new IllegalArgumentException("invalid command format; expected at least one body key");
    }
  }

  public static ParsedCommand parse(QueryMessage queryMsg) {
    checkArg(
        queryMsg.namespace().equals("admin.$cmd"),
        "do not know how to handle OP_QUERY for non-commands");
    try {
      String commandName = queryMsg.query().getFirstKey();
      return new ParsedCommand(commandName, queryMsg.query());
    } catch (NoSuchElementException ignored) {
      throw new IllegalArgumentException("invalid command format; expected at least one query key");
    }
  }

  public static ParsedCommand parse(InboundMessage msg) {
    if (msg instanceof QueryMessage queryMessage) {
      return parse(queryMessage);
    } else if (msg instanceof MessageMessage messageMessage) {
      return parse(messageMessage);
    } else {
      throw new UnsupportedOperationException(
          "do not know how to handle message " + msg.getHeader().opCode());
    }
  }
}
