package com.yungnickyoung.minecraft.bettercaves.noise;

import net.minecraft.world.World;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * Class used to generate NoiseTuples for blocks.
 * This class serves as an interface between Better Caves caves/caverns and FastNoise.
 */
public class NoiseGen {
    private long seed; // world seed should be used for reproducibility
    private FastNoise.NoiseType noiseType;

    /* ------------- Noise generation function parameters ------------- */
    private int fractalOctaves;
    private float fractalGain;
    private float fractalFrequency;

    /* ---------------- Turbulence function parameters ---------------- */
    private int turbulenceOctaves;
    private float turbulenceGain;
    private float turbulenceFrequency;

    /* ----- Switch for enabling turbulence---- */
    private boolean enableTurbulence;

    /* ----- Compression values - these control the size of caves ----- */
    /** Determines how steep and tall caves are */
    private float yCompression;
    /** Determines how horizontally large and stretched out caves are */
    private float xzCompression;

    /** List of all distinct noise generators */
    private List<FastNoise> listNoiseGens = new ArrayList<>();

    /** Turbulence generator */
    private FastNoise turbulenceGen = new FastNoise();

    /**
     *
     * @param noiseType The type of noise to generate, e.g. PerlinFractal, SimplexFractal, etc.
     * @param world World this generaton function will be used in
     * @param fOctaves Number of fractal octaves used in noise generation
     * @param fGain Amount of fractal gain used in noise generation
     * @param fFreq Fractal frequency used in noise generation
     * @param tOctaves Number of octaves used in the turbulence function
     * @param tGain Amount of gain used in the turbulence function
     * @param tFreq Frequency used in the turbulence function
     * @param useTurb Whether or not turbulence should be applied
     * @param yComp y-compression factor
     * @param xzComp xz-compression factor
     */
    public NoiseGen(FastNoise.NoiseType noiseType, World world,int fOctaves, float fGain, float fFreq,
                    int tOctaves, float tGain, float tFreq, boolean useTurb, float yComp, float xzComp) {
        this.noiseType = noiseType;
        this.seed = world.getSeed();
        this.fractalOctaves = fOctaves;
        this.fractalGain = fGain;
        this.fractalFrequency = fFreq;
        this.turbulenceOctaves = tOctaves;
        this.turbulenceGain = tGain;
        this.turbulenceFrequency = tFreq;
        this.enableTurbulence = useTurb;
        this.yCompression = yComp;
        this.xzCompression = xzComp;
        initializeTurbulenceGen();
    }

    /**
     * Generate NoiseTuples for a column of blocks, as a NoiseColumn.
     * @param chunkX Chunk x-coordinate
     * @param chunkZ Chunk z-coordinate
     * @param minHeight The bottom y-coordinate to start generating noise values for
     * @param maxHeight The top y-coordinate to stop generating noise values for
     * @param numGenerators Number of noise values to calculate per block. This number will be the number of noise
     *                      values in each resultant NoiseTuple. Increasing this will impact performance.
     * @param localX The chunk-local x-coordinate of the column of blocks (0-15, inclusive)
     * @param localZ The chunk-local z-coordinate of the column of blocks (0-15, inclusive)
     * @return NoiseColumn
     */
    public NoiseColumn generateNoiseColumn(int chunkX, int chunkZ, int minHeight, int maxHeight, int numGenerators, int localX, int localZ) {
        initializeNoiseGens(numGenerators);

        NoiseColumn noiseColumn = new NoiseColumn();
        int realX = localX + 16 * chunkX;
        int realZ = localZ + 16 * chunkZ;

        for (int y = minHeight; y <= maxHeight; y++) {
            Vector3f f = new Vector3f(realX * xzCompression, y * yCompression, realZ * xzCompression);

            // Use turbulence function to apply gradient perturbation, if enabled
            if (this.enableTurbulence)
                turbulenceGen.GradientPerturbFractal(f);

            // Create NoiseTuple for this block
            NoiseTuple newTuple = new NoiseTuple();
            for (int i = 0; i < numGenerators; i++)
                newTuple.put(listNoiseGens.get(i).GetNoise(f.x, f.y, f.z));

            noiseColumn.put(y, newTuple);
        }

        return noiseColumn;
    }

    /**
     * Generate NoiseTuples for a column of blocks, as a NoiseColumn.
     * Only blocks at the top & bottom of each subChunk (as designated by subChunkSize) have noise values actually
     * generated for them. Blocks in between have noise values estimated via bilinear interpolation.
     *
     * This function is currently unused, since such interpolation along the y-axis ends up reducing
     * cave interconnectivity. It is kept here for possible future use.
     * @param chunkX Chunk x-coordinate
     * @param chunkZ Chunk z-coordinate
     * @param minHeight The bottom y-coordinate to start generating noise values for
     * @param maxHeight The top y-coordinate to stop generating noise values for
     * @param numGenerators Number of noise values to calculate per block. This number will be the number of noise
     *                      values in each resultant NoiseTuple. Increasing this will impact performance.
     * @param localX The chunk-local x-coordinate of the column of blocks (0-15, inclusive)
     * @param localZ The chunk-local z-coordinate of the column of blocks (0-15, inclusive)
     * @param subChunkSize size of the subChunk, in blocks. Should be a power of 2.
     * @param startCoeffs Coefficients for the weight of the start noise value for linear interpolation
     * @param endCoeffs Coefficients for the weight of the end noise value for linear interpolation
     * @return NoiseColumn
     */
    public NoiseColumn interpolateNoiseColumn(int chunkX, int chunkZ, int minHeight, int maxHeight, int numGenerators,
                                              int localX, int localZ, int subChunkSize, float[] startCoeffs, float[] endCoeffs) {
        NoiseColumn noiseColumn = new NoiseColumn();
        initializeNoiseGens(numGenerators);
        int realX = localX + 16 * chunkX;
        int realZ = localZ + 16 * chunkZ;
        int startY;

        // Calculate noise for every nth block in the column, using bilinear interpolation for the rest
        for (startY = minHeight; startY <= maxHeight; startY += subChunkSize) {
            int endY = Math.min(startY + subChunkSize - 1, maxHeight);
            Vector3f subChunkStart = new Vector3f(realX * xzCompression, startY * yCompression, realZ * xzCompression);
            Vector3f subChunkEnd = new Vector3f(realX * xzCompression, endY * yCompression, realZ * xzCompression);

            // Use turbulence function to apply gradient perturbation, if enabled
            if (this.enableTurbulence) {
                turbulenceGen.GradientPerturbFractal(subChunkStart);
                turbulenceGen.GradientPerturbFractal(subChunkEnd);
            }

            // Create NoiseTuples for subchunk edge blocks
            NoiseTuple startTuple = new NoiseTuple();
            NoiseTuple endTuple = new NoiseTuple();
            for (int i = 0; i < numGenerators; i++) {
                startTuple.put(listNoiseGens.get(i).GetNoise(subChunkStart.x, subChunkStart.y, subChunkStart.z));
                endTuple.put(listNoiseGens.get(i).GetNoise(subChunkEnd.x, subChunkEnd.y, subChunkEnd.z));
            }
            noiseColumn.put(startY, startTuple);
            noiseColumn.put(endY, endTuple);

            // Fill in middle values via bilinear interpolation of edge values
            for (int y = startY + 1; y < endY; y++) {
                float startCoeff, endCoeff;
                if (endY == maxHeight) {
                    startCoeff = (float)(endY - startY - y - startY) / (endY - startY);
                    endCoeff = (float)(y - startY) / (endY - startY);
                } else {
                    startCoeff = startCoeffs[y - startY];
                    endCoeff = endCoeffs[y - startY];
                }
                NoiseTuple newTuple = startTuple
                        .times(startCoeff)
                        .plus(endTuple
                                .times(endCoeff));
                noiseColumn.put(y, newTuple);
            }
        }

        return noiseColumn;
    }

    /**
     * Generate NoiseTuples for a cube of blocks.
     * Only columns of blocks at the four corners of each cube have noise values calculated for them.
     * Blocks in between have noise values estimated via a naive implementation of trilinear interpolation.
     * @param chunkX Chunk x-coordinate
     * @param chunkZ Chunk z-coordinate
     * @param minHeight The bottom y-coordinate to start generating noise values for
     * @param maxHeight The top y-coordinate to stop generating noise values for
     * @param numGenerators Number of noise values to calculate per block. This number will be the number of noise
     *                      values in each resultant NoiseTuple. Increasing this will impact performance.
     * @param startX The chunk-local x-coordinate of the column of blocks (0-15, inclusive)
     * @param endX The chunk-local z-coordinate of the column of blocks (0-15, inclusive)
     * @param startZ The chunk-local x-coordinate of the column of blocks (0-15, inclusive)
     * @param endZ The chunk-local z-coordinate of the column of blocks (0-15, inclusive)
     * @param startCoeffs Coefficients for the weight of the start noise value for linear interpolation
     * @param endCoeffs Coefficients for the weight of the end noise value for linear interpolation
     * @return NoiseColumn
     */
    public List<List<NoiseColumn>> interpolateNoiseCube(int chunkX, int chunkZ, int minHeight, int maxHeight, int numGenerators,
                                                          int startX, int endX, int startZ, int endZ, float[] startCoeffs, float[] endCoeffs) {
        initializeNoiseGens(numGenerators);
        int subChunkSize = endX - startX + 1;
        float startCoeff, endCoeff;

        // Calculate noise tuples for four corner columns
        NoiseColumn noisesX0Z0 =
                generateNoiseColumn(chunkX, chunkZ, minHeight, maxHeight, numGenerators, startX, startZ);
        NoiseColumn noisesX0Z1 =
                generateNoiseColumn(chunkX, chunkZ, minHeight, maxHeight, numGenerators, startX, endZ);
        NoiseColumn noisesX1Z0 =
                generateNoiseColumn(chunkX, chunkZ, minHeight, maxHeight, numGenerators, endX, startZ);
        NoiseColumn noisesX1Z1 =
                generateNoiseColumn(chunkX, chunkZ, minHeight, maxHeight, numGenerators, endX, endZ);

        // Initialize cube with 4 corner columns
        List<List<NoiseColumn>> cube = new ArrayList<>();
        for (int x = 0; x < subChunkSize; x++) {
            List<NoiseColumn> xLayer = new ArrayList<>();
            for (int z = 0; z < subChunkSize; z++) {
                NoiseColumn col = new NoiseColumn();
                xLayer.add(col);
            }
            cube.add(xLayer);
        }
        cube.get(0).set(0, noisesX0Z0);
        cube.get(0).set(subChunkSize - 1, noisesX0Z1);
        cube.get(subChunkSize - 1).set(0, noisesX1Z0);
        cube.get(subChunkSize - 1).set(subChunkSize - 1, noisesX1Z1);

        // Populate edge planes along x axis
        for (int x = 1; x < subChunkSize - 1; x++) {
            startCoeff = startCoeffs[x];
            endCoeff = endCoeffs[x];

            NoiseColumn xz0 = cube.get(x).get(0);
            for (int y = minHeight; y <= maxHeight; y++) {
                NoiseTuple startTuple = cube.get(0).get(0).get(y);
                NoiseTuple endTuple = cube.get(subChunkSize - 1).get(0).get(y);
                NoiseTuple newTuple = startTuple
                        .times(startCoeff)
                        .plus(endTuple
                                .times(endCoeff));
                xz0.put(y, newTuple);
            }

            NoiseColumn xz1 = cube.get(x).get(subChunkSize - 1);
            for (int y = minHeight; y <= maxHeight; y++) {
                NoiseTuple startTuple = cube.get(0).get(subChunkSize - 1).get(y);
                NoiseTuple endTuple = cube.get(subChunkSize - 1).get(subChunkSize - 1).get(y);
                NoiseTuple newTuple = startTuple
                        .times(startCoeff)
                        .plus(endTuple
                                .times(endCoeff));
                xz1.put(y, newTuple);
            }
        }

        // Populate rest of cube by interpolating the two edge planes
        for (int x = 0; x < subChunkSize; x++) {
            for (int z = 1; z < subChunkSize - 1; z++) {
                startCoeff = startCoeffs[z];
                endCoeff = endCoeffs[z];

                NoiseColumn xz = cube.get(x).get(z);

                for (int y = minHeight; y <= maxHeight; y++) {
                    NoiseTuple startTuple = cube.get(x).get(0).get(y);
                    NoiseTuple endTuple = cube.get(x).get(subChunkSize - 1).get(y);
                    NoiseTuple newTuple = startTuple
                            .times(startCoeff)
                            .plus(endTuple
                                .times(endCoeff));

                    xz.put(y, newTuple);
                }
            }
        }

        return cube;
    }

    /* ------------------------- Public Getters -------------------------*/
    public long getSeed() {
        return seed;
    }

    public FastNoise.NoiseType getNoiseType() {
        return this.noiseType;
    }

    /* ------------------------- Private Methods -------------------------*/

    /**
     * Initialize generators if we haven't already initialized the number of generators passed in.
     * @param numGenerators Number of generators needed. If we've already initialized N generators, then this function
     *                      will initialize the {@code N - numGenerators} remaining generators.
     */
    private void initializeNoiseGens(int numGenerators) {
        if (numGenerators <= listNoiseGens.size())
            return; // We already have enough generators initialized

        int numGensNeeded = numGenerators - listNoiseGens.size();
        int seedModifier = listNoiseGens.size(); // Each seed must be different, but in a reproducible manner

        for (int i = 0; i < numGensNeeded; i++) {
            FastNoise noiseGen = new FastNoise();
            noiseGen.SetFractalType(FastNoise.FractalType.RigidMulti);
            noiseGen.SetSeed((int)(seed) + (1111 * seedModifier));
            noiseGen.SetNoiseType(this.noiseType);
            noiseGen.SetFractalOctaves(this.fractalOctaves);
            noiseGen.SetFractalGain(this.fractalGain);
            noiseGen.SetFrequency(this.fractalFrequency);

            listNoiseGens.add(noiseGen);
            seedModifier++;
        }

    }

    /**
     * Initialize the turbulence function
     */
    private void initializeTurbulenceGen() {
        turbulenceGen.SetNoiseType(FastNoise.NoiseType.PerlinFractal);
        turbulenceGen.SetFractalType(FastNoise.FractalType.FBM);
        turbulenceGen.SetSeed((int)(seed) + 69);
        turbulenceGen.SetFractalOctaves(this.turbulenceOctaves);
        turbulenceGen.SetFractalGain(this.turbulenceGain);
        turbulenceGen.SetFrequency(this.turbulenceFrequency);
    }
}
