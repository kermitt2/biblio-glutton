package com.scienceminer.glutton.storage.lookup;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class TransactionWrapper {
    public Txn<ByteBuffer> tx;

    TransactionWrapper(Txn<ByteBuffer> tx) {
        this.tx = tx;
    }
}