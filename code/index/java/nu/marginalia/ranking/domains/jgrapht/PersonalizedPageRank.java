package nu.marginalia.ranking.domains.jgrapht;

/*
 * (C) Copyright 2016-2023, by Dimitrios Michail and Contributors.
 *
 *
 * JGraphT : a free Java graph-theory library
 *
 * See the CONTRIBUTORS.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the
 * GNU Lesser General Public License v2.1 or later
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR LGPL-2.1-or-later
 */

/* (modified by @vlofgren to add personalization) */

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;

import java.util.*;

public class PersonalizedPageRank<V, E>
        implements VertexScoringAlgorithm<V, Double>
{
    /**
     * Default number of maximum iterations.
     */
    public static final int MAX_ITERATIONS_DEFAULT = 100;

    /**
     * Default value for the tolerance. The calculation will stop if the difference of PageRank
     * values between iterations change less than this value.
     */
    public static final double TOLERANCE_DEFAULT = 0.0001;

    /**
     * Damping factor default value.
     */
    public static final double DAMPING_FACTOR_DEFAULT = 0.85d;

    /**
     * The input graph
     */
    private final Graph<V, E> graph;
    private final Collection<V> influenceSet;

    /**
     * The damping factor
     */
    private final double dampingFactor;

    /**
     * Maximum iterations to run
     */
    private final int maxIterations;

    /**
     * The calculation will stop if the difference of PageRank values between iterations change less
     * than this value
     */
    private final double tolerance;

    /**
     * The result
     */
    private Map<V, Double> scores;

    /**
     * Create and execute an instance of Personalized PageRank.
     *
     * @param graph the input graph
     * @param influenceSet the set of vertices to personalize the Personalized PageRank calculation
     */
    public PersonalizedPageRank(Graph<V, E> graph, Collection<V> influenceSet)
    {
        this(graph, influenceSet, DAMPING_FACTOR_DEFAULT, MAX_ITERATIONS_DEFAULT, TOLERANCE_DEFAULT);
    }

    /**
     * Create and execute an instance of Personalized PageRank.
     *
     * @param graph the input graph
     * @param influenceSet the set of vertices to personalize the Personalized PageRank calculation
     * @param dampingFactor the damping factor
     */
    public PersonalizedPageRank(Graph<V, E> graph, Collection<V> influenceSet, double dampingFactor)
    {
        this(graph, influenceSet, dampingFactor, MAX_ITERATIONS_DEFAULT, TOLERANCE_DEFAULT);
    }

    /**
     * Create and execute an instance of Personalized PageRank.
     *
     * @param graph the input graph
     * @param influenceSet the set of vertices to personalize the Personalized PageRank calculation
     * @param dampingFactor the damping factor
     * @param maxIterations the maximum number of iterations to perform
     */
    public PersonalizedPageRank(Graph<V, E> graph, Collection<V> influenceSet, double dampingFactor, int maxIterations)
    {
        this(graph, influenceSet, dampingFactor, maxIterations, TOLERANCE_DEFAULT);
    }

    /**
     * Create and execute an instance of Personalized PageRank.
     *
     * @param graph the input graph
     * @param influenceSet the set of vertices to personalize the Personalized PageRank calculation
     * @param dampingFactor the damping factor
     * @param maxIterations the maximum number of iterations to perform
     * @param tolerance the calculation will stop if the difference of Personalized PageRank values between
     *        iterations change less than this value
     */
    public PersonalizedPageRank(Graph<V, E> graph, Collection<V> influenceSet, double dampingFactor, int maxIterations, double tolerance)
    {
        this.graph = graph;
        this.influenceSet = influenceSet;

        if (maxIterations <= 0) {
            throw new IllegalArgumentException("Maximum iterations must be positive");
        }
        this.maxIterations = maxIterations;

        if (dampingFactor < 0.0 || dampingFactor > 1.0) {
            throw new IllegalArgumentException("Damping factor not valid");
        }
        this.dampingFactor = dampingFactor;

        if (tolerance <= 0.0) {
            throw new IllegalArgumentException("Tolerance not valid, must be positive");
        }
        this.tolerance = tolerance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<V, Double> getScores()
    {
        if (scores == null) {
            scores = Collections.unmodifiableMap(new Algorithm().getScores());
        }
        return scores;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Double getVertexScore(V v)
    {
        if (!graph.containsVertex(v)) {
            throw new IllegalArgumentException("Cannot return score of unknown vertex");
        }
        return getScores().get(v);
    }

    /**
     * The actual implementation.
     *
     * <p>
     * We use this pattern with the inner class in order to be able to cache the result but also
     * allow the garbage collector to acquire all auxiliary memory used during the execution of the
     * algorithm.
     *
     * @author Dimitrios Michail
     *
     * @param <V> the graph type
     * @param <E> the edge type
     */
    private class Algorithm
    {
        private int totalVertices;
        private boolean isWeighted;

        private Map<V, Integer> vertexIndexMap;
        private V[] vertexMap;

        private double[] weightSum;
        private double[] curScore;
        private double[] nextScore;
        private int[] outDegree;
        private ArrayList<int[]> adjList;
        private ArrayList<double[]> weightsList;
        private BitSet influenceIndexSet;
        @SuppressWarnings("unchecked")
        public Algorithm()
        {
            this.totalVertices = graph.vertexSet().size();
            this.isWeighted = graph.getType().isWeighted();

            /*
             * Initialize score, map vertices to [0,n) and pre-compute degrees and adjacency lists
             */
            this.curScore = new double[totalVertices];
            this.nextScore = new double[totalVertices];
            this.vertexIndexMap = new HashMap<>();
            this.vertexMap = (V[]) new Object[totalVertices];
            this.outDegree = new int[totalVertices];
            this.adjList = new ArrayList<>(totalVertices);
            this.influenceIndexSet = new BitSet(totalVertices);

            double initScore = 1.0d / totalVertices;
            int i = 0;
            for (V v : graph.vertexSet()) {
                vertexIndexMap.put(v, i);
                vertexMap[i] = v;
                outDegree[i] = graph.outDegreeOf(v);
                curScore[i] = initScore;

                if (influenceSet.contains(v)) {
                    influenceIndexSet.set(i);
                }

                i++;
            }

            if (isWeighted) {
                this.weightSum = new double[totalVertices];
                this.weightsList = new ArrayList<>(totalVertices);

                for (i = 0; i < totalVertices; i++) {
                    V v = vertexMap[i];
                    int[] inNeighbors = new int[graph.inDegreeOf(v)];
                    double[] edgeWeights = new double[graph.inDegreeOf(v)];

                    int j = 0;
                    for (E e : graph.incomingEdgesOf(v)) {
                        V w = Graphs.getOppositeVertex(graph, e, v);
                        Integer mappedVertexId = vertexIndexMap.get(w);
                        inNeighbors[j] = mappedVertexId;
                        double edgeWeight = graph.getEdgeWeight(e);
                        edgeWeights[j] += edgeWeight;
                        weightSum[mappedVertexId] += edgeWeight;
                        j++;
                    }
                    weightsList.add(edgeWeights);
                    adjList.add(inNeighbors);
                }
            } else {
                for (i = 0; i < totalVertices; i++) {
                    V v = vertexMap[i];
                    int[] inNeighbors = new int[graph.inDegreeOf(v)];
                    int j = 0;
                    for (E e : graph.incomingEdgesOf(v)) {
                        V w = Graphs.getOppositeVertex(graph, e, v);
                        inNeighbors[j++] = vertexIndexMap.get(w);
                    }
                    adjList.add(inNeighbors);
                }
            }
        }

        public Map<V, Double> getScores()
        {
            // compute
            if (isWeighted) {
                runWeighted();
            } else {
                run();
            }

            // make results user friendly
            Map<V, Double> scores = new HashMap<>();
            for (int i = 0; i < totalVertices; i++) {
                V v = vertexMap[i];
                scores.put(v, curScore[i]);
            }
            return scores;
        }

        private void run()
        {
            double maxChange = tolerance;
            int iterations = maxIterations;

            while (iterations > 0 && maxChange >= tolerance) {
                double r = teleProp();

                maxChange = 0d;
                for (int i = 0; i < totalVertices; i++) {
                    double contribution = 0d;
                    for (int w : adjList.get(i)) {
                        contribution += dampingFactor * curScore[w] / outDegree[w];
                    }

                    double vOldValue = curScore[i];
                    double vNewValue = (influenceIndexSet.get(i) ? r : 0) + contribution;
                    maxChange = Math.max(maxChange, Math.abs(vNewValue - vOldValue));
                    nextScore[i] = vNewValue;
                }

                // progress
                swapScores();
                iterations--;
            }

            // remove influence factor from the scores
            double r = teleProp();
            for (int i = 0; i < totalVertices; i++) {
                curScore[i] -= (influenceIndexSet.get(i) ? r : 0);
            }
        }

        private void runWeighted()
        {
            double maxChange = tolerance;
            int iterations = maxIterations;

            while (iterations > 0 && maxChange >= tolerance) {
                double r = teleProp();

                maxChange = 0d;
                for (int i = 0; i < totalVertices; i++) {
                    double contribution = 0d;

                    int[] neighbors = adjList.get(i);
                    double[] weights = weightsList.get(i);
                    for (int j = 0, getLength = neighbors.length; j < getLength; j++) {
                        int w = neighbors[j];
                        contribution += dampingFactor * curScore[w] * weights[j] / weightSum[w];
                    }

                    double vOldValue = curScore[i];
                    double vNewValue = (influenceIndexSet.get(i) ? r : 0) + contribution;
                    maxChange = Math.max(maxChange, Math.abs(vNewValue - vOldValue));
                    nextScore[i] = vNewValue;
                }

                // progress
                swapScores();
                iterations--;
            }

            // remove influence factor from the scores
            double r = teleProp();
            for (int i = 0; i < totalVertices; i++) {
                curScore[i] -= (influenceIndexSet.get(i) ? r : 0);
            }
        }

        // This is the teleportation part of the algorithm, and also what is modified to personalize the PageRank
        private double teleProp()
        {
            double r = 0d;
            for (int v = influenceIndexSet.nextSetBit(0);
                 v >= 0;
                 v = influenceIndexSet.nextSetBit(v + 1))
            {
                if (outDegree[v] > 0)
                    r += (1d - dampingFactor);
                else
                    r += curScore[v];
            }
            return r / influenceSet.size();
        }

        private void swapScores()
        {
            double[] tmp = curScore;
            curScore = nextScore;
            nextScore = tmp;
        }

    }

}