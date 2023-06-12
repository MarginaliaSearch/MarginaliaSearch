package com.github.datquocnguyen;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;

/**
 * @author DatQuocNguyen
 * 
 */
public class RDRPOSTagger
{
	private final HashMap<String, String> FREQDICT;
	final int OUGHT_TO_BE_ENOUGH = 5000;
	final int CONTEXT_SIZE = 13;

	// Use dense array representation to reduce the level of indirection
	// and improve the performance of the tagger
	int[] conditions = new int[OUGHT_TO_BE_ENOUGH * CONTEXT_SIZE];
	String[] conclusions = new String[OUGHT_TO_BE_ENOUGH];
	short[] exceptIdx = new short[OUGHT_TO_BE_ENOUGH];
	short[] ifNotIdx = new short[OUGHT_TO_BE_ENOUGH];
	short[] fatherIdx = new short[OUGHT_TO_BE_ENOUGH];
	byte[] depthL = new byte[OUGHT_TO_BE_ENOUGH];

	short size = 0;

	private final TObjectIntHashMap<String> tagDict = new TObjectIntHashMap<>(10000, 0.75f, -1);

	private short addNode(FWObject condition, String conclusion, byte d) {
		short idx = size++;

		for (int i = 0; i < CONTEXT_SIZE; i++) {
			String context = condition.context[i];
			if (context != null) {
				tagDict.putIfAbsent(context, tagDict.size());

				conditions[idx * CONTEXT_SIZE + i] = tagDict.get(context);
			}
			else {
				conditions[idx * CONTEXT_SIZE + i] = -1;
			}
		}

		conclusions[idx] = conclusion;
		exceptIdx[idx] = -1;
		ifNotIdx[idx] = -1;
		fatherIdx[idx] = -1;
		depthL[idx] = d;

		return idx;
	}

	public RDRPOSTagger(Path dictPath, Path rulesFilePath) throws IOException {
		this.FREQDICT = Utils.getDictionary(dictPath.toString());
		Arrays.fill(conditions, -1);

		BufferedReader buffer = new BufferedReader(new InputStreamReader(
				new FileInputStream(rulesFilePath.toFile()), StandardCharsets.UTF_8));
		String line = buffer.readLine();

		short currentIdx = addNode(new FWObject(false), "NN", (byte) 0);
		byte currentDepth = 0;

		while ((line = buffer.readLine()) != null) {
			byte depth = 0;
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

			short newIdx = addNode(condition, conclusion, depth);

			if (depth > currentDepth) {
				exceptIdx[currentIdx] = newIdx;
			}
			else if (depth == currentDepth) {
				ifNotIdx[currentIdx] = newIdx;
			}
			else {
				while (depthL[currentIdx] != depth) {
					currentIdx = fatherIdx[currentIdx];
				}
				ifNotIdx[currentIdx] = newIdx;
			}

			fatherIdx[newIdx] = currentIdx;

			currentIdx = newIdx;
			currentDepth = depth;
		}
		buffer.close();
	}

	public String findFiredNode(FWObject object)
	{
		int currentIdx = 0;
		int firedIdx = -1;

		int[] objCtxI = object.objectCtxI;

		for (int i = 0; i < CONTEXT_SIZE; i++) {
			objCtxI[i] = tagDict.get(object.context[i]);
		}

		int[] conditionsL = conditions;
		short[] exceptIdxL = exceptIdx;
		short[] ifNotIdxL = ifNotIdx;

		while (currentIdx >= 0) {
			if (satisfy(objCtxI, conditionsL, currentIdx)) {
				firedIdx = currentIdx;
				currentIdx = exceptIdxL[currentIdx];
			}
			else {
				currentIdx = ifNotIdxL[currentIdx];
			}
		}

		if (firedIdx >= 0) {
			return conclusions[firedIdx];
		}
		else {
			return "";
		}
	}

	public boolean satisfy(int[] objectCtxI, int[] conditions, int contextIdx)
	{
		// This is a good candidate for a vector operation
		for (int i = 0; i < CONTEXT_SIZE; i++) {
			int key = conditions[CONTEXT_SIZE *contextIdx + i];
			if (key >= 0 && key != objectCtxI[i]) {
				return false;
			}
		}
		return true;
	}

	public String[] tagsForEnSentence(String[] sentence)
	{

		var initialTags = InitialTagger.EnInitTagger4Sentence(FREQDICT, sentence);

		String[] tags = new String[initialTags.length];
		FWObject object = new FWObject(true);

		for (int i = 0; i < initialTags.length; i++) {
			Utils.getObject(object, sentence, initialTags, initialTags.length, i);
			tags[i] = findFiredNode(object);
		}

		return tags;
	}

}
