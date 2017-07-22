package com.mysafeatl.atlcomp.mysafeatl.repository;

import android.util.Log;

public class ThreadedStack {

    private Node top;
    private int size = 0;

    public boolean isEmpty(){
        return top == null;
    }

    public int stackSize(){
        return size;
    }

    public boolean stackAdd(String data){
        Node newNode = new Node();
        newNode.setData(data);
        if(isEmpty()){
            newNode.setNext(top);
        }
        top = newNode;
        size++;
        return true;
    }

    public String getTop(){
        if(isEmpty()){
            return "Stack empty!";
        }
        return top.getData();
    }

    public String stackRemove(){
        if(isEmpty()){
            return "Stack empty";
        }
        String data = top.getData();
        top = top.getNext();
        size--;
        return data;
    }

    public void stackPrint(){
        Node helper = top;
        while(helper != null){
            helper = helper.getNext();
        }
    }
}
