/*
*   GLTools -- A collection of static methods for working with 3D graphics.
*
*   Copyright (C) 2001-2004 by Joseph A. Huwaldt
*   All rights reserved.
*   
*   This library is free software; you can redistribute it and/or
*   modify it under the terms of the GNU Lesser General Public
*   License as published by the Free Software Foundation; either
*   version 2 of the License, or (at your option) any later version.
*   
*   This library is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
*   Lesser General Public License for more details.
*
*   You should have received a copy of the GNU Lesser General Public License
*   along with this program; if not, write to the Free Software
*   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*   Or visit:  http://www.gnu.org/licenses/lgpl.html
**/
package sg.edu.nus.comp.android3dvisualisationtool.app.util;


/**
 * A set of static utility methods for working with 3D graphics.
 * In particular, these methods are geared for working efficiently
 * with GL4Java and OpenGL.
 * <p/>
 * <p>  Modified by:  Joseph A. Huwaldt  </p>
 *
 * @author Joseph A. Huwaldt   Date:  January 13, 2001
 * @version July 24, 2004
 */
public class GLTools {

    // Some constants for convenience.
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    //	Some error messages.
    private static final String kVector3DMsg = "Vectors must be 3D (3 elements).";
    private static final String kPoint3DMsg = "Points must be 3D (3 elements).";
    private static final String kNormal3DMsg = "Normal vector must be 3D (3 elements).";


    /**
     * This method will normalize a 3D vector to have
     * a length of 1.0.  The unit vector is placed in the
     * specified output vector (which may safely be a reference
     * to the input vector).
     *
     * @param vector The 3D (3 element) vector to be normalized.
     * @param output The 3D (3 element) vector that will contain
     *               the normalized version of the input vector.
     * @return A reference to the output normalized vector.
     */
    public static final float[] normalize(float[] vector, float[] output) {
        if (vector.length != 3 || output.length != 3)
            throw new IllegalArgumentException(kVector3DMsg);

        float v0 = vector[X], v1 = vector[Y], v2 = vector[Z];
        float length = (float) Math.sqrt(v0 * v0 + v1 * v1 + v2 * v2);

        //	Do not allow a divide by zero.
        if (length == 0)
            length = 1;

        //	Divide each element by the length to get a unit vector.
        output[X] = v0 / length;
        output[Y] = v1 / length;
        output[Z] = v2 / length;

        return output;
    }

    /**
     * This method will normalize a 3D vector to have
     * a length of 1.0.  A new vector is created that is the normalized
     * version of the input vector.
     *
     * @param vector The 3D (3 element) vector to be normalized.
     * @return A reference to the new output normalized vector.
     */
    public static final float[] normalize(float vector[]) {
        if (vector.length != 3)
            throw new IllegalArgumentException(kVector3DMsg);

        float v0 = vector[X], v1 = vector[Y], v2 = vector[Z];
        float length = (float) Math.sqrt(v0 * v0 + v1 * v1 + v2 * v2);

        //	Do not allow a divide by zero.
        if (length == 0)
            length = 1;

        //	Divide each element by the length to get a unit vector.
        float[] output = new float[3];
        output[X] = v0 / length;
        output[Y] = v1 / length;
        output[Z] = v2 / length;

        return output;
    }

    /**
     * Calculate the normal vector for a plane (triangle) defined
     * by 3 points in space.  The points must be specified in
     * counterclockwise order.  The normal vector is placed in the
     * specified output vector.
     *
     * @param pnt1   The 1st 3D point in the plane.
     * @param pnt2   The 2nd 3D point in the plane.
     * @param pnt3   The 3rd 3D point in the plane.
     * @param output The 3D vector (3 element array) that will be filled in
     *               with the normal vector for this plane.
     * @return A reference to the output normal vector.
     */
    public static final float[] calcNormal(float[] pnt1, float[] pnt2, float[] pnt3,
                                           float[] output) {
        if (pnt1.length != 3 || pnt2.length != 3 || pnt3.length != 3)
            throw new IllegalArgumentException(kPoint3DMsg);
        if (output.length != 3)
            throw new IllegalArgumentException(kNormal3DMsg);

        //	Calculate two vectors from the three points.
        float v1x = pnt1[X] - pnt2[X];
        float v1y = pnt1[Y] - pnt2[Y];
        float v1z = pnt1[Z] - pnt2[Z];

        float v2x = pnt2[X] - pnt3[X];
        float v2y = pnt2[Y] - pnt3[Y];
        float v2z = pnt2[Z] - pnt3[Z];

        //	Take the cross product of the two vectors and store in output.
        output[X] = v1y * v2z - v1z * v2y;
        output[Y] = v1z * v2x - v1x * v2z;
        output[Z] = v1x * v2y - v1y * v2x;

        //	Normalize the resulting vector and return it.
        return normalize(output, output);
    }

    /**
     * Calculate the normal vector for a plane (triangle) defined
     * by 3 points in space.  The points must be specified in
     * counterclockwise order.  A new normal vector is created and
     * returned by this method.
     *
     * @param pnt1 The 1st 3D point in the plane.
     * @param pnt2 The 2nd 3D point in the plane.
     * @param pnt3 The 3rd 3D point in the plane.
     * @return A reference to the new 3D output normal vector.
     */
    public static final float[] calcNormal(float[] pnt1, float[] pnt2, float[] pnt3) {
        if (pnt1.length != 3 || pnt2.length != 3 || pnt3.length != 3)
            throw new IllegalArgumentException(kPoint3DMsg);

        //	Calculate two vectors from the three points.
        float v1x = pnt1[X] - pnt2[X];
        float v1y = pnt1[Y] - pnt2[Y];
        float v1z = pnt1[Z] - pnt2[Z];

        float v2x = pnt2[X] - pnt3[X];
        float v2y = pnt2[Y] - pnt3[Y];
        float v2z = pnt2[Z] - pnt3[Z];

        //	Take the cross product of the two vectors and store in output.
        float[] output = new float[3];
        output[X] = v1y * v2z - v1z * v2y;
        output[Y] = v1z * v2x - v1x * v2z;
        output[Z] = v1x * v2y - v1y * v2x;

        //	Normalize the resulting vector and return it.
        return normalize(output, output);
    }

    /**
     * Returns the length of a 3D vector.
     *
     * @param vector A 3D (3 element) vector.
     * @return The length of the input vector.
     */
    public static final float length3D(float[] vector) {
        if (vector.length != 3)
            throw new IllegalArgumentException(kVector3DMsg);
        double dx = vector[X];
        double dy = vector[Y];
        double dz = vector[Z];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Returns the dot product of two 3D vectors.
     *
     * @param a The 1st 3D (3 element) vector.
     * @param b The 2nd 3D (3 element) vector.
     * @return The dot product of the two vectors.
     */
    public static final float dotProduct3D(float[] a, float[] b) {
        if (a.length != 3 || b.length != 3)
            throw new IllegalArgumentException(kVector3DMsg);
        return (a[X] * b[X] + a[Y] * b[Y] + a[Z] * b[Z]);
    }

    /**
     * Returns the right-handed cross product of 3D vectors A and B in AcrossB.
     *
     * @param A       The 1st 3D (3 element) vector.
     * @param B       The 2nd 3D (3 element) vector.
     * @param AcrossB A 3D (3 element) vector to be filled in with the cross
     *                product of A and B.  This vector will be overwritten
     *                by this method.
     * @return A reference to the input vector AcrossB.
     */
    public static final float[] crossProduct3D(float[] A, float[] B, float[] AcrossB) {
        if (A.length != 3 || B.length != 3 || AcrossB.length != 3)
            throw new IllegalArgumentException(kVector3DMsg);
        AcrossB[X] = A[Y] * B[Z] - A[Z] * B[Y];
        AcrossB[Y] = A[Z] * B[X] - A[X] * B[Z];
        AcrossB[Z] = A[X] * B[Y] - A[Y] * B[X];
        return AcrossB;
    }

    /**
     * A method for calculating 3D Euclidian distance or length.
     *
     * @param dx The difference between X coordinates (X2 - X1).
     * @param dy The difference between Y coordinates (Y2 - Y1).
     * @param dz The difference between Z coordinates (Z2 - Z1).
     * @return The distance between the two points.
     */
    public static final float distance(double dx, double dy, double dz) {
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * A method for calculating the distance between two
     * points specified as arrays with 3 elements.
     *
     * @param pnt1 An array with 3 elements (X,Y,Z) representing the 1st point.
     * @param pnt2 An array with 3 elements (X,Y,Z) representing the 2nd point.
     * @return The distance between the two points.
     */
    public static final float distance(float[] pnt1, float[] pnt2) {
        if (pnt1.length != 3 || pnt2.length != 3)
            throw new IllegalArgumentException(kPoint3DMsg);
        double dx = pnt2[X] - pnt1[X];
        double dy = pnt2[Y] - pnt1[Y];
        double dz = pnt2[Z] - pnt1[Z];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * A method for calculating the distance between two
     * points specified as arrays with 3 elements.
     *
     * @param pnt1 An array with 3 elements (X,Y,Z) representing the 1st point.
     * @param pnt2 An array with 3 elements (X,Y,Z) representing the 2nd point.
     * @return The distance between the two points.
     */
    public static final float distance(int[] pnt1, int[] pnt2) {
        if (pnt1.length != 3 || pnt2.length != 3)
            throw new IllegalArgumentException(kPoint3DMsg);
        int dx = pnt2[X] - pnt1[X];
        int dy = pnt2[Y] - pnt1[Y];
        int dz = pnt2[Z] - pnt1[Z];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * A fast approximation to 3D Euclidian distance or length!
     * Accurate to within 13%. Also knowns as the "Manhattan" distance.
     * This method approximates 3D distance as follows:
     * <code>
     * dist = sqrt(dx*dx + dy*dy + dz*dz)
     * approx. dist = max + (med + min)/4
     * </code>
     * Uses shifting and adding.  No multiplication, division or sqrt calculations.
     *
     * @param dx The difference between X coordinates (X2 - X1).
     * @param dy The difference between Y coordinates (Y2 - Y1).
     * @param dz The difference between Z coordinates (Z2 - Z1).
     * @return The approximate distance between two points.
     */
    public static final int distanceApprox(int dx, int dy, int dz) {
        int maxc = dx > 0 ? dx : -dx;        //	Math.abs(dx);
        int medc = dy > 0 ? dy : -dy;        //	Math.abs(dy);
        int minc = dz > 0 ? dz : -dz;        //	Math.abs(dz);

        if (maxc < medc) {
            int temp = maxc;
            maxc = medc;
            medc = temp;
        }
        if (maxc < minc) {
            int temp = maxc;
            maxc = minc;
            minc = temp;
        }

        medc += minc;
        medc >>= 2;
        maxc += medc;

        return maxc;
    }

    /**
     * A fast approximation to 2D Euclidian distance or length!
     * Also knowns as the "Manhattan" distance.
     * This method approximates 2D distance as follows:
     * <code>
     * dist = sqrt(dx*dx + dy*dy)
     * approx. dist = max + min/4
     * </code>
     * Uses shifting and adding.  No multiplication, division or sqrt calculations.
     *
     * @param dx The difference between X coordinates (X2 - X1).
     * @param dy The difference between Y coordinates (Y2 - Y1).
     * @return The approximate distance between two points.
     */
    public static final int distanceApprox(int dx, int dy) {
        int maxc = dx > 0 ? dx : -dx;        //	Math.abs(dx);
        int minc = dy > 0 ? dy : -dy;        //	Math.abs(dy);

        if (maxc < minc) {
            int temp = maxc;
            maxc = minc;
            minc = temp;
        }

        minc >>= 2;
        maxc += minc;

        return maxc;
    }

    //	****  The following are used by the CORDIC fixed point trigonometry routines ****
//	private static final int COSCALE=0x11616E8E;	//	291597966 = 0.2715717684432241*2^30, valid for j>13
    private static final int QUARTER = 90 << 16;
    private static final int MAXITER = 22;
    //	Table of Cordic values for arctangent in degrees.
    private static final int arctantab[] = {
            4157273, 2949120, 1740967, 919879, 466945, 234379, 117304, 58666,
            29335, 14668, 7334, 3667, 1833, 917, 458, 229,
            115, 57, 29, 14, 7, 4, 2, 1
    };
    private static int xtemp, ytemp;    //	Temporary storage space used by CORDIC methods.


    /**
     * <p> Converts fixed point coordinates (b,a) to polar coordinates (r, theta) and
     * returns the angle theta in degrees as a fixed point integer number.
     * This method is about 40% faster than Math.atan2() using 22 iterations,
     * but a small amount of accuracy is lost.  It is 50% faster than Math.atan2()
     * using 11 iterations and is still good to +/- 1 degree. <p>
     * <p/>
     * <p> This code is based on that found in "Cordic.c" by Ken Turkowski.
     * http://www.worldserver.com/turk/opensource/Cordic.c.txt.
     * The original code carried the following notice: <p>
     * <code>
     * Copyright (C) 1981-1999 Ken Turkowski.
     * All rights reserved.
     * Warranty Information
     * Even though I have reviewed this software, I make no warranty
     * or representation, either express or implied, with respect to this
     * software, its quality, accuracy, merchantability, or fitness for a
     * particular purpose.  As a result, this software is provided "as is,"
     * and you, its user, are assuming the entire risk as to its quality
     * and accuracy.
     * <p/>
     * This code may be used and freely distributed as long as it includes
     * this copyright notice and the above warranty information.
     * </code>
     * <p/>
     * <p>  WARNING!!  This method is not thread safe!  It stores intermediate results in
     * a static class variable.  Sorry, needed to do that for speed.  </p>
     *
     * @param iter The number of CORDIC iterations to compute.  Will be clamped to (1 <= iter <= 22).
     *             More iterations gives more accurate results, but takes longer.
     * @param a    The first fixed point coordinate value (a = (int)(y)<<16).
     * @param b    The 2nd fixed point coordinate value (b = (int)(x)<<16).
     * @return The fixed point polar coordinate angle theta is returned (double theta = value/65536.).
     * The angle returned is in degrees and is (0 <= theta < 360).
     */
    public static final int fixedAtan2D(int iter, int a, int b) {
        if ((a == 0) && (b == 0))
            return 0;

        fxPreNorm(a, b);		/* Pre-normalize vector for maximum precision */

        b = pseudoPolarize(xtemp, ytemp, iter);	/* Convert to polar coordinates */

        //	Get into a 0 to 360 degree range.
        b = 90 * (1 << 16) - b;
        if (b < 0)
            b += 360 << 16;
        return b;
    }

    /**
     * This method converts the fixed point input cartesian values
     * to fixed point polar notation.  On completion of this routine
     * xtemp contains the radius and ytemp contains the angle.  The
     * angle is also returned by this method.
     * This code is based on that found in "Cordic.c" by Ken Turkowski.
     */
    private static int pseudoPolarize(int x, int y, int iter) {

		/* Get the vector into the right half plane */
        int theta = 0;
        if (x < 0) {
            x = -x;
            y = -y;
            theta = 2 * QUARTER;
        }

        if (y > 0)
            theta = -theta;

        int[] arctanptr = arctantab;
        int yi = 0, pos = 0;
        if (y < 0) {	/* Rotate positive */
            yi = y + (x << 1);
            x = x - (y << 1);
            y = yi;
            theta -= arctanptr[pos++];	/* Subtract angle */
        } else {		/* Rotate negative */
            yi = y - (x << 1);
            x = x + (y << 1);
            y = yi;
            theta += arctanptr[pos++];	/* Add angle */
        }

        if (iter > MAXITER)
            iter = MAXITER;
        else if (iter < 1)
            iter = 1;

        for (int i = 0; i <= iter; i++) {
            if (y < 0) {	/* Rotate positive */
                yi = y + (x >> i);
                x = x - (y >> i);
                y = yi;
                theta -= arctanptr[pos++];
            } else {		/* Rotate negative */
                yi = y - (x >> i);
                x = x + (y >> i);
                y = yi;
                theta += arctanptr[pos++];
            }
        }

        xtemp = x;
        ytemp = theta;
        return theta;
    }

    /**
     * FxPreNorm() block normalizes the arguments to a magnitude suitable for
     * CORDIC pseudorotations.  The returned value is the block exponent.
     * This code is based on that found in "Cordic.c" by Ken Turkowski.
     */
    private static int fxPreNorm(int x, int y) {
        int signx = 1, signy = 1;

        if (x < 0) {
            x = -x;
            signx = -signx;
        }
        if (y < 0) {
            y = -y;
            signy = -signy;
        }

        int shiftexp = 0;	/* Block normalization exponent */

		/* Prenormalize vector for maximum precision */
        if (x < y) {	/* |y| > |x| */
            while (y < (1 << 27)) {
                x <<= 1;
                y <<= 1;
                shiftexp--;
            }
            while (y > (1 << 28)) {
                x >>= 1;
                y >>= 1;
                shiftexp++;
            }
        } else {		/* |x| > |y| */
            while (x < (1 << 27)) {
                x <<= 1;
                y <<= 1;
                shiftexp--;
            }
            while (x > (1 << 28)) {
                x >>= 1;
                y >>= 1;
                shiftexp++;
            }
        }

        xtemp = (signx < 0) ? -x : x;
        ytemp = (signy < 0) ? -y : y;
        return shiftexp;
    }

    /**
     * Method that will get the specified angle into the range
     * 0 <= angle < 360 by repeatedly adding or subtracting 360.
     *
     * @param angle The angle to get into range 0 <= angle < 360
     *              in degrees.
     */
    public static final float range360(float angle) {
        while (angle < 0)
            angle += 360;
        while (angle >= 360)
            angle -= 360;

        return angle;
    }

    /**
     * Method that will get the specified angle into the range
     * 0 <= angle < 360 by repeatedly adding or subtracting 360.
     *
     * @param angle The angle to get into range 0 <= angle < 360
     *              in degrees.
     */
    public static final int range360(int angle) {
        while (angle < 0)
            angle += 360;
        while (angle >= 360)
            angle -= 360;

        return angle;
    }

    //	The following is used by sinD() and cosD() methods.
    private static float[] cosTable;

    /**
     * This method must be called to initialize the sine and cosine
     * lookup tables before using the sinD() and cosD() methods
     * for the first time.
     */
    public static final void initSinDCosD() {
        if (cosTable == null) {
            int count = 360 + 90;    //	The extra 90 degs allows sin & cos to use the same table.

            float[] table = new float[count];
            for (int i = 0; i < count; ++i) {
                table[i] = (float) Math.cos(i * Math.PI / 180.);
            }

            cosTable = table;
        }
    }

    /**
     * Quickly returns the exact cosine of an angle in degrees with
     * 1 degree resolution.  This is done by the use of a lookup
     * table that must be initialized by calling "initSinDCosD()"
     * before calling this method for the first time.
     *
     * @param angle The angle to calculate the cosine for in degrees.
     *              The angle must be in the range:  0 <= angle < 360.
     */
    public static final float cosD(int angle) {
        return cosTable[angle];
    }

    /**
     * Quickly returns the exact sine of an angle in degrees with
     * 1 degree resolution.  This is done by the use of a lookup
     * table that must be initialized by calling "initSinDCosD()"
     * before calling this method for the first time.
     *
     * @param angle The angle to calculate the sine for in degrees.
     *              The angle must be in the range:  0 <= angle < 360.
     */
    public static final float sinD(int angle) {
        angle += 90;
        return -cosTable[angle];
    }

}
