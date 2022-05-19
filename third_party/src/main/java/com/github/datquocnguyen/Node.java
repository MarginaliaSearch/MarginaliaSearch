package com.github.datquocnguyen;

/**
 * @author DatQuocNguyen
 * 
 */

public class Node
{
	FWObject condition;
	String conclusion;
	Node exceptNode;
	Node ifnotNode;
	Node fatherNode;
	int depth;

	public Node(FWObject inCondition, String inConclusion, Node inFatherNode,
		Node inExceptNode, Node inIfnotNode, int inDepth)
	{
		this.condition = inCondition;
		this.conclusion = inConclusion;
		this.fatherNode = inFatherNode;
		this.exceptNode = inExceptNode;
		this.ifnotNode = inIfnotNode;
		this.depth = inDepth;
	}

	public void setIfnotNode(Node node)
	{
		this.ifnotNode = node;
	}

	public void setExceptNode(Node node)
	{
		this.exceptNode = node;
	}

	public void setFatherNode(Node node)
	{
		this.fatherNode = node;
	}

	public int countNodes()
	{
		int count = 1;
		if (exceptNode != null) {
			count += exceptNode.countNodes();
		}
		if (ifnotNode != null) {
			count += ifnotNode.countNodes();
		}
		return count;
	}

	public boolean satisfy(FWObject object)
	{
		for (int i = 0; i < 13; i++) {
			String key = condition.context[i];
			if (key != null) {
				if (!key.equals(object.context[i])) {
					return false;
				}
			}
		}
		return true;
	}
}
