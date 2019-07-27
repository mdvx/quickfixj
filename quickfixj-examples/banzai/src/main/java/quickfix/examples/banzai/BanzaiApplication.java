/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved.
 *
 * This file is part of the QuickFIX FIX Engine
 *
 * This file may be distributed under the terms of the quickfixengine.org
 * license as defined by quickfixengine.org and appearing in the file
 * LICENSE included in the packaging of this file.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE.
 *
 * See http://www.quickfixengine.org/LICENSE for licensing information.
 *
 * Contact ask@quickfixengine.org if any conditions of this licensing
 * are not clear to you.
 ******************************************************************************/

package quickfix.examples.banzai;

import org.knowm.xchange.coinbasepro.service.CoinbaseProDigest;
import quickfix.*;
import quickfix.field.*;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

public class BanzaiApplication implements Application {
    private final DefaultMessageFactory messageFactory = new DefaultMessageFactory();
    private OrderTableModel orderTableModel = null;
    private ExecutionTableModel executionTableModel = null;
    private final ObservableOrder observableOrder = new ObservableOrder();
    private final ObservableLogon observableLogon = new ObservableLogon();
    private boolean isAvailable = true;
    private boolean isMissingField;

    static private final TwoWayMap sideMap = new TwoWayMap();
    static private final TwoWayMap typeMap = new TwoWayMap();
    static private final TwoWayMap tifMap = new TwoWayMap();
    static private final HashMap<SessionID, HashSet<ExecID>> execIDs = new HashMap<>();

    public BanzaiApplication(OrderTableModel orderTableModel,
            ExecutionTableModel executionTableModel) {
        this.orderTableModel = orderTableModel;
        this.executionTableModel = executionTableModel;
    }

    public void onCreate(SessionID sessionID) {
    }

    public void onLogon(SessionID sessionID) {
        observableLogon.logon(sessionID);
    }

    public void onLogout(SessionID sessionID) {
        observableLogon.logoff(sessionID);
    }

    public void toAdmin(quickfix.Message message, SessionID sessionID) {
        MsgType msgType = new MsgType();
        MsgSeqNum msgSeqNum = new MsgSeqNum();
        SendingTime sendingTime = new SendingTime();
        SenderCompID senderCompID = new SenderCompID();
        TargetCompID targetCompID = new TargetCompID();
        Password passphrase = new Password("ppht398ss2");

        String secretKeyBase64 = "llEzihglxB3oX/pbj028M4S6xP0l8a8czhcHmimgpNAZYvLuxTVFDfGhQd887YZ7BJkjD3gX0v9ICp6y/s9o+w==";

        try {
            /*
                The Logon message sent by the client must be signed for security. The signing method is described in Signing
                a Message. The prehash string is the following fields joined by the FIX field separator (ASCII code 1):

                SendingTime, MsgType, MsgSeqNum, SenderCompID, TargetCompID, Password.

                There is no trailing separator. The RawData field should be a base64 encoding of the HMAC signature.
             */
            if (message.getHeader().getField(msgType).valueEquals("A")) { // Logon Message
                message.setField(passphrase);
//                message.setField(new StringField(8013,"s"));
//                message.setField(new StringField(9406,"Y"));

                // Always sign
//                LocalDateTime value = message.getHeader().getField(sendingTime).getValue();
//                long mills = Timestamp.valueOf(value).getTime();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS");

                String preSign = new StringBuilder()
                        .append(message.getHeader().getField(sendingTime).getValue().format(formatter)).append('\001')
                        .append(message.getHeader().getField(msgType).getValue()).append('\001')
                        .append(message.getHeader().getField(msgSeqNum).getValue()).append('\001')
                        .append(message.getHeader().getField(senderCompID).getValue()).append('\001')
                        .append(message.getHeader().getField(targetCompID).getValue()).append('\001')
                        .append("ppht398ss2")
                        .toString();

                final SecretKeySpec secretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(secretKeyBase64), CoinbaseProDigest.HMAC_SHA_256);


                Mac mac256 = Mac.getInstance(CoinbaseProDigest.HMAC_SHA_256);
                mac256.init(secretKeySpec);

                mac256.update(preSign.getBytes(StandardCharsets.US_ASCII));
                String signature = Base64.getEncoder().encodeToString(mac256.doFinal());

                message.setField(new RawData(signature));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void toApp(quickfix.Message message, SessionID sessionID) throws DoNotSend {
    }

    public void fromAdmin(quickfix.Message message, SessionID sessionID) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, RejectLogon {
    }

    public void fromApp(quickfix.Message message, SessionID sessionID) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        try {
            SwingUtilities.invokeLater(new MessageProcessor(message, sessionID));
        } catch (Exception e) {
        }
    }

    public class MessageProcessor implements Runnable {
        private final quickfix.Message message;
        private final SessionID sessionID;

        public MessageProcessor(quickfix.Message message, SessionID sessionID) {
            this.message = message;
            this.sessionID = sessionID;
        }

        public void run() {
            try {
                MsgType msgType = new MsgType();
                if (isAvailable) {
                    if (isMissingField) {
                        // For OpenFIX certification testing
                        sendBusinessReject(message, BusinessRejectReason.CONDITIONALLY_REQUIRED_FIELD_MISSING, "Conditionally required field missing");
                    }
                    else if (message.getHeader().isSetField(DeliverToCompID.FIELD)) {
                        // This is here to support OpenFIX certification
                        sendSessionReject(message, SessionRejectReason.COMPID_PROBLEM);
                    } else if (message.getHeader().getField(msgType).valueEquals("8")) {
                        executionReport(message, sessionID);
                    } else if (message.getHeader().getField(msgType).valueEquals("9")) {
                        cancelReject(message, sessionID);
                    } else if (message.getHeader().getField(msgType).valueEquals("j")) {
                        JOptionPane.showMessageDialog(null, message.toString());
                    } else {
                        sendBusinessReject(message, BusinessRejectReason.UNSUPPORTED_MESSAGE_TYPE,
                                "Unsupported Message Type");
                    }
                } else {
                    sendBusinessReject(message, BusinessRejectReason.APPLICATION_NOT_AVAILABLE,
                            "Application not available");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendSessionReject(Message message, int rejectReason) throws FieldNotFound,
            SessionNotFound {
        Message reply = createMessage(message, MsgType.REJECT);
        reverseRoute(message, reply);
        String refSeqNum = message.getHeader().getString(MsgSeqNum.FIELD);
        reply.setString(RefSeqNum.FIELD, refSeqNum);
        reply.setString(RefMsgType.FIELD, message.getHeader().getString(MsgType.FIELD));
        reply.setInt(SessionRejectReason.FIELD, rejectReason);
        Session.sendToTarget(reply);
    }

    private void sendBusinessReject(Message message, int rejectReason, String rejectText)
            throws FieldNotFound, SessionNotFound {
        Message reply = createMessage(message, MsgType.BUSINESS_MESSAGE_REJECT);
        reverseRoute(message, reply);
        String refSeqNum = message.getHeader().getString(MsgSeqNum.FIELD);
        reply.setString(RefSeqNum.FIELD, refSeqNum);
        reply.setString(RefMsgType.FIELD, message.getHeader().getString(MsgType.FIELD));
        reply.setInt(BusinessRejectReason.FIELD, rejectReason);
        reply.setString(Text.FIELD, rejectText);
        Session.sendToTarget(reply);
    }

    private Message createMessage(Message message, String msgType) throws FieldNotFound {
        return messageFactory.create(message.getHeader().getString(BeginString.FIELD), msgType);
    }

    private void reverseRoute(Message message, Message reply) throws FieldNotFound {
        reply.getHeader().setString(SenderCompID.FIELD,
                message.getHeader().getString(TargetCompID.FIELD));
        reply.getHeader().setString(TargetCompID.FIELD,
                message.getHeader().getString(SenderCompID.FIELD));
    }

    private void executionReport(Message message, SessionID sessionID) throws FieldNotFound {

        ExecID execID = (ExecID) message.getField(new ExecID());
        if (alreadyProcessed(execID, sessionID))
            return;

        Order order = null;
        if (message.isSetField(ClOrdID.FIELD)) {
            String clOrdId = message.getString(ClOrdID.FIELD);
            order = orderTableModel.getOrder(clOrdId);
        }
        else {
            order = new Order();
            order.setSessionID(sessionID);

            if (message.isSetField(Symbol.FIELD))
                order.setSymbol(message.getString(Symbol.FIELD));

            if (message.isSetField(OrderQty.FIELD))
                order.setQuantity(message.getDecimal(OrderQty.FIELD));

            orderTableModel.addID(order, order.getID());
        }

        BigDecimal fillSize = BigDecimal.ZERO;

        if (message.isSetField(LastShares.FIELD)) {
            LastShares lastShares = new LastShares();
            message.getField(lastShares);
            fillSize = new BigDecimal("" + lastShares.getValue());
        } else if (message.isSetField(LeavesQty.FIELD)){
            // > FIX 4.1
            LeavesQty leavesQty = new LeavesQty();
            message.getField(leavesQty);
            fillSize = order.getQuantity().subtract(new BigDecimal("" + leavesQty.getValue()));
        }

        if (fillSize.compareTo(BigDecimal.ZERO) > 0) {
            order.setOpen(order.getOpen().subtract(new BigDecimal(fillSize.toPlainString())));
            if (message.isSetField(CumQty.FIELD))
                order.setExecuted(Double.parseDouble(message.getString(CumQty.FIELD)));

            if (message.isSetField(AvgPx.FIELD))
                order.setAvgPx(Double.parseDouble(message.getString(AvgPx.FIELD)));
        }

        if (message.isSetField(OrdStatus.FIELD)) {
            OrdStatus ordStatus = (OrdStatus) message.getField(new OrdStatus());

            if (ordStatus.valueEquals(OrdStatus.REJECTED)) {
                order.setRejected(true);
                order.setOpen(BigDecimal.ZERO);
            } else if (ordStatus.valueEquals(OrdStatus.CANCELED)
                    || ordStatus.valueEquals(OrdStatus.DONE_FOR_DAY)) {
                order.setCanceled(true);
                order.setOpen(BigDecimal.ZERO);
            } else if (ordStatus.valueEquals(OrdStatus.NEW)) {
                if (order.isNew()) {
                    order.setNew(false);
                }
            }
        }

        if (message.isSetField(Text.FIELD))
            order.setMessage(message.getField(new Text()).getValue());


        orderTableModel.updateOrder(order, order.getID());
        observableOrder.update(order);

        Execution execution = new Execution();
        execution.setExecID(sessionID + message.getField(new ExecID()).getValue());
        execution.setSymbol(message.getField(new Symbol()).getValue());

        execution.setQuantity(fillSize);
        if (message.isSetField(LastPx.FIELD))
            execution.setPrice(new BigDecimal(message.getString(LastPx.FIELD)));

        if (message.isSetField(Side.FIELD))
            execution.setSide(FIXSideToSide((Side) message.getField(new Side())));

        if (message.isSetField(ExecID.FIELD))
            execution.setExecID(message.getString(ExecID.FIELD));

        if (message.isSetField(SecurityExchange.FIELD))
            execution.setExchange(message.getString(SecurityExchange.FIELD));

        if (message.isSetField(ClOrdID.FIELD))
            execution.setClOrdId(message.getString(ClOrdID.FIELD));

        if (message.isSetField(OrderID.FIELD))
            execution.setID(message.getString(OrderID.FIELD));

        if (message.isSetField(Text.FIELD))
            execution.setText(message.getString(Text.FIELD));

        executionTableModel.addExecution(execution);
    }

    private void cancelReject(Message message, SessionID sessionID) throws FieldNotFound {

        String id = message.getField(new ClOrdID()).getValue();
        Order order = orderTableModel.getOrder(id);
        if (order == null)
            return;
        if (order.getOriginalID() != null)
            order = orderTableModel.getOrder(order.getOriginalID());

        try {
            order.setMessage(message.getField(new Text()).getValue());
        } catch (FieldNotFound e) {
        }
        orderTableModel.updateOrder(order, message.getField(new OrigClOrdID()).getValue());
    }

    private boolean alreadyProcessed(ExecID execID, SessionID sessionID) {
        HashSet<ExecID> set = execIDs.get(sessionID);
        if (set == null) {
            set = new HashSet<>();
            set.add(execID);
            execIDs.put(sessionID, set);
            return false;
        } else {
            if (set.contains(execID))
                return true;
            set.add(execID);
            return false;
        }
    }

    private void send(quickfix.Message message, SessionID sessionID) {
        try {
            Session.sendToTarget(message, sessionID);
        } catch (SessionNotFound e) {
            System.out.println(e);
        }
    }

    public void send(Order order) {
        String beginString = order.getSessionID().getBeginString();
        switch (beginString) {
            case FixVersions.BEGINSTRING_FIX40:
                send40(order);
                break;
            case FixVersions.BEGINSTRING_FIX41:
                send41(order);
                break;
            case FixVersions.BEGINSTRING_FIX42:
                send42(order);
                break;
            case FixVersions.BEGINSTRING_FIX43:
                send43(order);
                break;
            case FixVersions.BEGINSTRING_FIX44:
                send44(order);
                break;
            case FixVersions.BEGINSTRING_FIXT11:
                send50(order);
                break;
        }
    }

    public void send40(Order order) {
        quickfix.fix40.NewOrderSingle newOrderSingle = new quickfix.fix40.NewOrderSingle(
                new ClOrdID(order.getID()), new HandlInst('1'), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()), new OrderQty(order.getQuantity().doubleValue()),
                typeToFIXType(order.getType()));

        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public void send41(Order order) {
        quickfix.fix41.NewOrderSingle newOrderSingle = new quickfix.fix41.NewOrderSingle(
                new ClOrdID(order.getID()), new HandlInst('1'), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()), typeToFIXType(order.getType()));
        newOrderSingle.set(new OrderQty(order.getQuantity().doubleValue()));

        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public void send42(Order order) {
        quickfix.fix42.NewOrderSingle newOrderSingle = new quickfix.fix42.NewOrderSingle(
                new ClOrdID(order.getID()), new HandlInst('1'), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()), new TransactTime(), typeToFIXType(order.getType()));
        newOrderSingle.set(new OrderQty(order.getQuantity().doubleValue()));

        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public void send43(Order order) {
        quickfix.fix43.NewOrderSingle newOrderSingle = new quickfix.fix43.NewOrderSingle(
                new ClOrdID(order.getID()), new HandlInst('1'), sideToFIXSide(order.getSide()),
                new TransactTime(), typeToFIXType(order.getType()));
        newOrderSingle.set(new OrderQty(order.getQuantity().doubleValue()));
        newOrderSingle.set(new Symbol(order.getSymbol()));
        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public void send44(Order order) {
        quickfix.fix44.NewOrderSingle newOrderSingle = new quickfix.fix44.NewOrderSingle(
                new ClOrdID(order.getID()), sideToFIXSide(order.getSide()),
                new TransactTime(), typeToFIXType(order.getType()));
        newOrderSingle.set(new OrderQty(order.getQuantity().doubleValue()));
        newOrderSingle.set(new Symbol(order.getSymbol()));
        newOrderSingle.set(new HandlInst('1'));
        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public void send50(Order order) {
        quickfix.fix50.NewOrderSingle newOrderSingle = new quickfix.fix50.NewOrderSingle(
                new ClOrdID(order.getID()), sideToFIXSide(order.getSide()),
                new TransactTime(), typeToFIXType(order.getType()));
        newOrderSingle.set(new OrderQty(order.getQuantity().doubleValue()));
        newOrderSingle.set(new Symbol(order.getSymbol()));
        newOrderSingle.set(new HandlInst('1'));
        send(populateOrder(order, newOrderSingle), order.getSessionID());
    }

    public quickfix.Message populateOrder(Order order, quickfix.Message newOrderSingle) {

        OrderType type = order.getType();

        newOrderSingle.setField(new ClOrdID(order.getID()));

        if (type == OrderType.LIMIT)
            newOrderSingle.setField(new Price(order.getLimit()));
        else if (type == OrderType.STOP) {
            newOrderSingle.setField(new StopPx(order.getStop()));
        } else if (type == OrderType.STOP_LIMIT) {
            newOrderSingle.setField(new Price(order.getLimit()));
            newOrderSingle.setField(new StopPx(order.getStop()));
        }

        if (order.getSide() == OrderSide.SHORT_SELL
                || order.getSide() == OrderSide.SHORT_SELL_EXEMPT) {
            newOrderSingle.setField(new LocateReqd(false));
        }

        newOrderSingle.setField(tifToFIXTif(order.getTIF()));
        if (order.getSecurityExchange().length() > 0)
            newOrderSingle.setField(new SecurityExchange(order.getSecurityExchange()));
        else {
            newOrderSingle.setField(new TargetStrategy(-07450));  // 847
            newOrderSingle.setField(new TargetStrategyParameters("{ 'strategy': 'SOR' }"));  // 848
        }

        newOrderSingle.setField(new TimeInForce(TimeInForce.GOOD_TILL_CANCEL));

        return newOrderSingle;
    }

    public void cancel(Order order) {
        String beginString = order.getSessionID().getBeginString();
        switch (beginString) {
            case "FIX.4.0":
                cancel40(order);
                break;
            case "FIX.4.1":
                cancel41(order);
                break;
            case "FIX.4.2":
                cancel42(order);
                break;
            case "FIX.4.4":
                cancel44(order);
                break;
        }
    }

    public void cancel40(Order order) {
        String id = order.generateID();
        quickfix.fix40.OrderCancelRequest message = new quickfix.fix40.OrderCancelRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(id), new CxlType(CxlType.FULL_REMAINING_QUANTITY), new Symbol(order
                        .getSymbol()), sideToFIXSide(order.getSide()), new OrderQty(order
                        .getQuantity().doubleValue()));

        orderTableModel.addID(order, id);
        send(message, order.getSessionID());
    }

    public void cancel41(Order order) {
        String id = order.generateID();
        quickfix.fix41.OrderCancelRequest message = new quickfix.fix41.OrderCancelRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(id), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()));
        message.setField(new OrderQty(order.getQuantity().doubleValue()));

        orderTableModel.addID(order, id);
        send(message, order.getSessionID());
    }

    public void cancel42(Order order) {
        String id = order.generateID();
        quickfix.fix42.OrderCancelRequest message = new quickfix.fix42.OrderCancelRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(id), new Symbol(order.getSymbol()),
                sideToFIXSide(order.getSide()), new TransactTime());
        message.setField(new OrderQty(order.getQuantity().doubleValue()));

        orderTableModel.addID(order, id);
        send(message, order.getSessionID());
    }
    public void cancel44(Order order) {

        quickfix.fix44.OrderCancelRequest message = new quickfix.fix44.OrderCancelRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(order.getID()),
                sideToFIXSide(order.getSide()), new TransactTime());
        message.setField(new OrderID(order.getID()));
        message.setField(new OrderQty(order.getQuantity().doubleValue()));
        message.setField(new Symbol(order.getSymbol()));

        orderTableModel.addID(order, order.getID());
        send(message, order.getSessionID());
    }

    public void replace(Order order, Order newOrder) {
        String beginString = order.getSessionID().getBeginString();
        switch (beginString) {
            case "FIX.4.0":
                replace40(order, newOrder);
                break;
            case "FIX.4.1":
                replace41(order, newOrder);
                break;
            case "FIX.4.2":
                replace42(order, newOrder);
                break;
            case "FIX.4.4":
                replace44(order, newOrder);
                break;
        }
    }

    public void replace40(Order order, Order newOrder) {
        quickfix.fix40.OrderCancelReplaceRequest message = new quickfix.fix40.OrderCancelReplaceRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(newOrder.getID()), new HandlInst('1'),
                new Symbol(order.getSymbol()), sideToFIXSide(order.getSide()), new OrderQty(
                        newOrder.getQuantity().doubleValue()), typeToFIXType(order.getType()));

        orderTableModel.addID(order, newOrder.getID());
        send(populateCancelReplace(order, newOrder, message), order.getSessionID());
    }

    public void replace41(Order order, Order newOrder) {
        quickfix.fix41.OrderCancelReplaceRequest message = new quickfix.fix41.OrderCancelReplaceRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(newOrder.getID()), new HandlInst('1'),
                new Symbol(order.getSymbol()), sideToFIXSide(order.getSide()), typeToFIXType(order.getType()));

        orderTableModel.addID(order, newOrder.getID());
        send(populateCancelReplace(order, newOrder, message), order.getSessionID());
    }

    public void replace42(Order order, Order newOrder) {
        quickfix.fix42.OrderCancelReplaceRequest message = new quickfix.fix42.OrderCancelReplaceRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(newOrder.getID()), new HandlInst('1'),
                new Symbol(order.getSymbol()), sideToFIXSide(order.getSide()), new TransactTime(),
                typeToFIXType(order.getType()));

        orderTableModel.addID(order, newOrder.getID());
        send(populateCancelReplace(order, newOrder, message), order.getSessionID());
    }
    public void replace44(Order order, Order newOrder) {
        quickfix.fix44.OrderCancelReplaceRequest message = new quickfix.fix44.OrderCancelReplaceRequest(
                new OrigClOrdID(order.getID()), new ClOrdID(newOrder.getID()),
                sideToFIXSide(order.getSide()), new TransactTime(),
                typeToFIXType(order.getType()));

        message.setField( new HandlInst('1'));
        message.setField( new Symbol(order.getSymbol()));

        orderTableModel.addID(order, newOrder.getID());
        send(populateCancelReplace(order, newOrder, message), order.getSessionID());
    }

    Message populateCancelReplace(Order order, Order newOrder, quickfix.Message message) {

        if (order.getQuantity() != newOrder.getQuantity())
            message.setField(new OrderQty(newOrder.getQuantity().doubleValue()));
        if (!order.getLimit().equals(newOrder.getLimit()))
            message.setField(new Price(newOrder.getLimit()));
        return message;
    }

    public Side sideToFIXSide(OrderSide side) {
        return (Side) sideMap.getFirst(side);
    }

    public OrderSide FIXSideToSide(Side side) {
        return (OrderSide) sideMap.getSecond(side);
    }

    public OrdType typeToFIXType(OrderType type) {
        return (OrdType) typeMap.getFirst(type);
    }

    public OrderType FIXTypeToType(OrdType type) {
        return (OrderType) typeMap.getSecond(type);
    }

    public TimeInForce tifToFIXTif(OrderTIF tif) {
        return (TimeInForce) tifMap.getFirst(tif);
    }

    public OrderTIF FIXTifToTif(TimeInForce tif) {
        return (OrderTIF) typeMap.getSecond(tif);
    }

    public void addLogonObserver(Observer observer) {
        observableLogon.addObserver(observer);
    }

    public void deleteLogonObserver(Observer observer) {
        observableLogon.deleteObserver(observer);
    }

    public void addOrderObserver(Observer observer) {
        observableOrder.addObserver(observer);
    }

    public void deleteOrderObserver(Observer observer) {
        observableOrder.deleteObserver(observer);
    }

    private static class ObservableOrder extends Observable {
        public void update(Order order) {
            setChanged();
            notifyObservers(order);
            clearChanged();
        }
    }

    private static class ObservableLogon extends Observable {
        private final HashSet<SessionID> set = new HashSet<>();

        public void logon(SessionID sessionID) {
            set.add(sessionID);
            setChanged();
            notifyObservers(new LogonEvent(sessionID, true));
            clearChanged();
        }

        public void logoff(SessionID sessionID) {
            set.remove(sessionID);
            setChanged();
            notifyObservers(new LogonEvent(sessionID, false));
            clearChanged();
        }
    }

    static {
        sideMap.put(OrderSide.BUY, new Side(Side.BUY));
        sideMap.put(OrderSide.SELL, new Side(Side.SELL));
        sideMap.put(OrderSide.SHORT_SELL, new Side(Side.SELL_SHORT));
        sideMap.put(OrderSide.SHORT_SELL_EXEMPT, new Side(Side.SELL_SHORT_EXEMPT));
        sideMap.put(OrderSide.CROSS, new Side(Side.CROSS));
        sideMap.put(OrderSide.CROSS_SHORT, new Side(Side.CROSS_SHORT));

        typeMap.put(OrderType.MARKET, new OrdType(OrdType.MARKET));
        typeMap.put(OrderType.LIMIT, new OrdType(OrdType.LIMIT));
        typeMap.put(OrderType.STOP, new OrdType(OrdType.STOP_STOP_LOSS));
        typeMap.put(OrderType.STOP_LIMIT, new OrdType(OrdType.STOP_LIMIT));

        tifMap.put(OrderTIF.DAY, new TimeInForce(TimeInForce.DAY));
        tifMap.put(OrderTIF.IOC, new TimeInForce(TimeInForce.IMMEDIATE_OR_CANCEL));
        tifMap.put(OrderTIF.OPG, new TimeInForce(TimeInForce.AT_THE_OPENING));
        tifMap.put(OrderTIF.GTC, new TimeInForce(TimeInForce.GOOD_TILL_CANCEL));
        tifMap.put(OrderTIF.GTX, new TimeInForce(TimeInForce.GOOD_TILL_CROSSING));
    }

    public boolean isMissingField() {
        return isMissingField;
    }

    public void setMissingField(boolean isMissingField) {
        this.isMissingField = isMissingField;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean isAvailable) {
        this.isAvailable = isAvailable;
    }
}
