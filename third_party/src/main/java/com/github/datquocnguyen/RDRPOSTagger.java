package com.github.datquocnguyen;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;

/**
 * @author DatQuocNguyen
 * 
 */
public class RDRPOSTagger
{
	private final HashMap<String, String> FREQDICT;
	public final Node root;

	public RDRPOSTagger(Path dictPath, Path rulesFilePath) throws IOException {
		this.FREQDICT = Utils.getDictionary(dictPath.toString());

		BufferedReader buffer = new BufferedReader(new InputStreamReader(
				new FileInputStream(rulesFilePath.toFile()), StandardCharsets.UTF_8));
		String line = buffer.readLine();

		this.root = new Node(new FWObject(false), "NN", null, null, null, 0);

		Node currentNode = this.root;
		int currentDepth = 0;

		while ((line = buffer.readLine()) != null) {
			int depth = 0;
			for (int i = 0; i <= 6; i++) { // Supposed that the maximum
				// exception level is up to 6.
				if (line.charAt(i) == '\t')
					depth += 1;
				else
					break;
			}

			line = line.trim();
			if (line.length() == 0)
				continue;

			if (line.contains("cc:"))
				continue;

			FWObject condition = Utils
					.getCondition(line.split(" : ")[0].trim());
			String conclusion = Utils.getConcreteValue(line.split(" : ")[1]
					.trim());

			Node node = new Node(condition, conclusion, null, null, null, depth);

			if (depth > currentDepth) {
				currentNode.setExceptNode(node);
			}
			else if (depth == currentDepth) {
				currentNode.setIfnotNode(node);
			}
			else {
				while (currentNode.depth != depth)
					currentNode = currentNode.fatherNode;
				currentNode.setIfnotNode(node);
			}
			node.setFatherNode(currentNode);

			currentNode = node;
			currentDepth = depth;
		}
		buffer.close();
	}

	public Node findFiredNode(FWObject object)
	{
		Node currentN = root;
		Node firedN = null;
		while (true) {
			if (currentN.satisfy(object)) {
				firedN = currentN;
				if (currentN.exceptNode == null) {
					break;
				}
				else {
					currentN = currentN.exceptNode;
				}
			}
			else {
				if (currentN.ifnotNode == null) {
					break;
				}
				else {
					currentN = currentN.ifnotNode;
				}
			}

		}

		return firedN;
	}

	public String[] tagsForEnSentence(String[] sentence)
	{

		var initialTags = InitialTagger.EnInitTagger4Sentence(FREQDICT, sentence);

		String[] tags = new String[initialTags.length];
		FWObject object = new FWObject(true);

		for (int i = 0; i < initialTags.length; i++) {
			Utils.getObject(object, sentence, initialTags, initialTags.length, i);
			tags[i] = findFiredNode(object).conclusion;
		}

		return tags;
	}

}
