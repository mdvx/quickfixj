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

import javax.swing.table.AbstractTableModel;
import java.util.HashMap;

public class ExecutionTableModel extends AbstractTableModel {

    private final static int SYMBOL = 0;
    private final static int QUANTITY = 1;
    private final static int ORD_STATUS = 2;  // SIDE
    private final static int PRICE = 3;
    private final static int TEXT = 4;
    private final static int EXEC_ID = 5;
    private final static int EXCHANGE = 6;
    private final static int ORDER_ID = 7;
    private final static int CL_ORD_ID = 8;
    private final static int EXEC_TYPE = 9;
    private final static int STRATEGY = 10;

    private final HashMap<Integer, Execution> rowToExecution;
    private final HashMap<String, Integer> idToRow;
    private final HashMap<String, Execution> idToExecution;
    private final HashMap<String, Execution> exchangeIdToExecution;

    private final String[] headers;

    public ExecutionTableModel() {
        rowToExecution = new HashMap<>();
        idToRow = new HashMap<>();
        idToExecution = new HashMap<>();
        exchangeIdToExecution = new HashMap<>();

        headers = new String[] {"Symbol", "38=OrdQty", "39=OrdStatus", "44=Price", "48=Text", "17=ExecID", "207=Exchange", "37=ExchangeID", "11=clOrdID", "150=ExecType","848=Strategy"};
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public void addExecution(Execution execution) {
        int row = rowToExecution.size();

        if (exchangeIdToExecution.get(execution.getExecID()) != null)
            return;

        rowToExecution.put(row, execution);
        idToRow.put(execution.getID(), row);
        idToExecution.put(execution.getID(), execution);
        exchangeIdToExecution.put(execution.getExecID(), execution);

        fireTableRowsInserted(row, row);
    }

    public Execution getExchangeExecution(String exchangeID) {
        return exchangeIdToExecution.get(exchangeID);
    }

    public Execution getExecution(int row) {
        return rowToExecution.get(row);
    }

    public void setValueAt(Object value, int rowIndex, int columnIndex) { }

    public Class<String> getColumnClass(int columnIndex) {
        return String.class;
    }

    public int getRowCount() {
        return rowToExecution.size();
    }

    public int getColumnCount() {
        return headers.length;
    }

    public String getColumnName(int columnIndex) {
        return headers[columnIndex];
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        Execution execution = rowToExecution.get(rowIndex);

        switch (columnIndex) {
            case SYMBOL:
                return execution.getSymbol();
            case QUANTITY:
                return execution.getQuantity();
            case ORD_STATUS:
                return execution.getOrdStatus();
            case PRICE:
                return execution.getPrice();
            case TEXT:
                return execution.getText();
            case EXEC_ID:
                return execution.getExecID();
            case EXCHANGE:
                return execution.getExchange();
            case ORDER_ID:
                return execution.getID();
            case CL_ORD_ID:
                return execution.getClOrdId();
            case EXEC_TYPE:
                return execution.getExecType();
            case STRATEGY:
                return execution.getStrategy();
        }
        return "";
    }
}
