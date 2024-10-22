/*
*   Matrix -- Collection of methods for working with 3D transformation matricies.
*   
*   Copyright (C) 2001-2002 by Joseph A. Huwaldt <jhuwaldt@knology.net>.
*   All rights reserved.
*   
*   This library is free software; you can redistribute it and/or
*   modify it under the terms of the GNU Library General Public
*   License as published by the Free Software Foundation; either
*   version 2 of the License, or (at your option) any later version.
*   
*   This library is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
*   Library General Public License for more details.
**/
package sg.edu.nus.comp.android3dvisualisationtool.app.util;


/**
 * A set of static utility methods for working with matricies.
 * In particular, these methods are geared for working efficiently
 * with OpenGL transformation matrices.
 * <p/>
 * <p>  Modified by:  Joseph A. Huwaldt  </p>
 *
 * @author Joseph A. Huwaldt   Date:  January 13, 2001
 * @version January 30, 2001
 */
public class Matrix {

    /**
     * The value for Math.PI as a float instead of a double.
     */
    public static final float PI = (float) Math.PI;

    /**
     * Convert's an angle in radians to one in degrees.
     *
     * @param rad The angle in radians to be converted.
     * @return The angle in degrees.
     */
    public static final float rad2Deg(float rad) {
        return rad * 180 / PI;
    }

    /**
     * Convert's an angle in degrees to one in radians.
     *
     * @param deg The angle in degrees to be converted.
     * @return The angle in radians.
     */
    public static final float deg2Rad(float deg) {
        return deg * PI / 180;
    }

    /**
     * Set a 4x4 transformation matrix to the identity matrix.
     * Equiv. to: T = I
     * <p/>
     * I =  1   0   0   0
     * 0   1   0   0
     * 0   0   1   0
     * 0   0   0   1
     *
     * @param mtx The 4x4 matrix to be set.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] setIdentity(float mtx[]) {
        mtx[0] = mtx[5] = mtx[10] = mtx[15] = 1;
        mtx[1] = mtx[2] = mtx[3] = mtx[4] = 0;
        mtx[6] = mtx[7] = mtx[8] = mtx[9] = 0;
        mtx[11] = mtx[12] = mtx[13] = mtx[14] = 0;

        return mtx;
    }

    /**
     * Create a new 4x4 transformation matrix set to the identity matrix.
     * Equiv. to: T = I
     * <p/>
     * I =  1   0   0   0
     * 0   1   0   0
     * 0   0   1   0
     * 0   0   0   1
     *
     * @return A reference to a 4x4 identity matrix is returned.
     */
    public static final float[] identity() {
        float[] mtx = new float[16];
        mtx[0] = mtx[5] = mtx[10] = mtx[15] = 1;
        return mtx;
    }

    /**
     * Set a 4x4 transformation matrix which rotates about the X-axis.
     * Equiv. to: T = Rx
     * <p/>
     * Rx =  1   0           0         0
     * 0   cos(a)      sin(a)    0
     * 0   -sin(a)     cos(a)    0
     * 0   0           0         1
     *
     * @param angle The rotation angle about the X-axis in radians.
     * @param mtx   The 4x4 matrix to be set.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] setRotateX(float angle, float mtx[]) {
        float rsin = (float) Math.sin(angle);
        float rcos = (float) Math.cos(angle);
        mtx[0] = 1.0f;
        mtx[1] = 0.0f;
        mtx[2] = 0.0f;
        mtx[3] = 0.0f;
        mtx[4] = 0.0f;
        mtx[5] = rcos;
        mtx[6] = rsin;
        mtx[7] = 0.0f;
        mtx[8] = 0.0f;
        mtx[9] = -rsin;
        mtx[10] = rcos;
        mtx[11] = 0.0f;
        mtx[12] = 0.0f;
        mtx[13] = 0.0f;
        mtx[14] = 0.0f;
        mtx[15] = 1.0f;

        return mtx;
    }

    /**
     * Set a 4x4 transformation matrix which rotates about the Y-axis.
     * Equiv. to: T = Ry
     * <p/>
     * Ry =  1   0           0         0
     * 0   cos(a)      sin(a)    0
     * 0   -sin(a)     cos(a)    0
     * 0   0           0         1
     *
     * @param angle The rotation angle about the Y-axis in radians.
     * @param mtx   The 4x4 matrix to be set.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] setRotateY(float angle, float mtx[]) {
        float rsin = (float) Math.sin(angle);
        float rcos = (float) Math.cos(angle);
        mtx[0] = rcos;
        mtx[1] = 0.0f;
        mtx[2] = -rsin;
        mtx[3] = 0.0f;
        mtx[4] = 0.0f;
        mtx[5] = 1.0f;
        mtx[6] = 0.0f;
        mtx[7] = 0.0f;
        mtx[8] = rsin;
        mtx[9] = 0.0f;
        mtx[10] = rcos;
        mtx[11] = 0.0f;
        mtx[12] = 0.0f;
        mtx[13] = 0.0f;
        mtx[14] = 0.0f;
        mtx[15] = 1.0f;

        return mtx;
    }

    /**
     * Set a 4x4 transformation matrix which rotates about the Z-axis.
     * Equiv. to: T = Rz
     * <p/>
     * Rz =  1   0           0         0
     * 0   cos(a)      sin(a)    0
     * 0   -sin(a)     cos(a)    0
     * 0   0           0         1
     *
     * @param angle The rotation angle about the Z-axis in radians.
     * @param mtx   The 4x4 matrix to be set.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] setRotateZ(float angle, float mtx[]) {
        float rsin = (float) Math.sin(angle);
        float rcos = (float) Math.cos(angle);
        mtx[0] = rcos;
        mtx[1] = rsin;
        mtx[2] = 0.0f;
        mtx[3] = 0.0f;
        mtx[4] = -rsin;
        mtx[5] = rcos;
        mtx[6] = 0.0f;
        mtx[7] = 0.0f;
        mtx[8] = 0.0f;
        mtx[9] = 0.0f;
        mtx[10] = 1.0f;
        mtx[11] = 0.0f;
        mtx[12] = 0.0f;
        mtx[13] = 0.0f;
        mtx[14] = 0.0f;
        mtx[15] = 1.0f;

        return mtx;
    }

    /**
     * Set a 4x4 transformation matrix which scales in each
     * axis direction by the specified scale factors.
     * Equiv. to: T = S
     * <p/>
     * S =   sx  0   0   0
     * 0   sy  0   0
     * 0   0   sz  0
     * 0   0   0   1
     *
     * @param sx  The scale factor in the X-axis direction.
     * @param sy  The scale factor in the Y-axis direction.
     * @param sz  The scale factor in the Z-axis direction.
     * @param mtx The 4x4 matrix to be set.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] setScale(float sx, float sy, float sz, float mtx[]) {
        mtx[0] = sx;
        mtx[5] = sy;
        mtx[10] = sz;
        mtx[15] = 1;
        mtx[1] = mtx[2] = mtx[3] = mtx[4] = 0;
        mtx[6] = mtx[7] = mtx[8] = mtx[9] = 0;
        mtx[11] = mtx[12] = mtx[13] = mtx[14] = 0;

        return mtx;
    }

    /**
     * Set a 4x4 transformation matrix which translates in each
     * axis direction by the specified amounts.
     * Equiv. to: T = Tr
     * <p/>
     * Tr =  1   0   0   tx
     * 0   1   0   ty
     * 0   0   1   tz
     * 0   0   0   1
     *
     * @param tx  The translation amount in the X-axis direction.
     * @param ty  The translation amount in the Y-axis direction.
     * @param tz  The translation amount in the Z-axis direction.
     * @param mtx The 4x4 matrix to be set.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] setTranslate(float tx, float ty, float tz, float mtx[]) {
        mtx[0] = 1;
        mtx[1] = mtx[2] = 0;
        mtx[3] = tx;
        mtx[4] = 0;
        mtx[5] = 1;
        mtx[6] = 0;
        mtx[7] = ty;
        mtx[8] = mtx[9] = 0;
        mtx[10] = 1;
        mtx[11] = tz;
        mtx[12] = mtx[13] = mtx[14] = 0;
        mtx[15] = 1;

        return mtx;
    }

    /**
     * Multiplies two 4x4 matricies together, and returns the
     * result in a new output matrix. The multiplication is done
     * fast, at the expense of code.
     *
     * @param mtx1 The 1st matrix being multiplied.
     * @param mtx2 The 2nd matrix being multiplied.
     * @return The output matrix containing the input matrices multiplied.
     */
    public static final float[] multiply(float mtx1[], float mtx2[]) {
        float nmtx[] = new float[16];

        float m0 = mtx1[0], m1 = mtx1[1], m2 = mtx1[2], m3 = mtx1[3];
        nmtx[0] = m0 * mtx2[0] + m1 * mtx2[4] + m2 * mtx2[8] + m3 * mtx2[12];
        nmtx[1] = m0 * mtx2[1] + m1 * mtx2[5] + m2 * mtx2[9] + m3 * mtx2[13];
        nmtx[2] = m0 * mtx2[2] + m1 * mtx2[6] + m2 * mtx2[10] + m3 * mtx2[14];
        nmtx[3] = m0 * mtx2[3] + m1 * mtx2[7] + m2 * mtx2[11] + m3 * mtx2[15];

        m0 = mtx1[4];
        m1 = mtx1[5];
        m2 = mtx1[6];
        m3 = mtx1[7];
        nmtx[4] = m0 * mtx2[0] + m1 * mtx2[4] + m2 * mtx2[8] + m3 * mtx2[12];
        nmtx[5] = m0 * mtx2[1] + m1 * mtx2[5] + m2 * mtx2[9] + m3 * mtx2[13];
        nmtx[6] = m0 * mtx2[2] + m1 * mtx2[6] + m2 * mtx2[10] + m3 * mtx2[14];
        nmtx[7] = m0 * mtx2[3] + m1 * mtx2[7] + m2 * mtx2[11] + m3 * mtx2[15];

        m0 = mtx1[8];
        m1 = mtx1[9];
        m2 = mtx1[10];
        m3 = mtx1[11];
        nmtx[8] = m0 * mtx2[0] + m1 * mtx2[4] + m2 * mtx2[8] + m3 * mtx2[12];
        nmtx[9] = m0 * mtx2[1] + m1 * mtx2[5] + m2 * mtx2[9] + m3 * mtx2[13];
        nmtx[10] = m0 * mtx2[2] + m1 * mtx2[6] + m2 * mtx2[10] + m3 * mtx2[14];
        nmtx[11] = m0 * mtx2[3] + m1 * mtx2[7] + m2 * mtx2[11] + m3 * mtx2[15];

        m0 = mtx1[12];
        m1 = mtx1[13];
        m2 = mtx1[14];
        m3 = mtx1[15];
        nmtx[12] = m0 * mtx2[0] + m1 * mtx2[4] + m2 * mtx2[8] + m3 * mtx2[12];
        nmtx[13] = m0 * mtx2[1] + m1 * mtx2[5] + m2 * mtx2[9] + m3 * mtx2[13];
        nmtx[14] = m0 * mtx2[2] + m1 * mtx2[6] + m2 * mtx2[10] + m3 * mtx2[14];
        nmtx[15] = m0 * mtx2[3] + m1 * mtx2[7] + m2 * mtx2[11] + m3 * mtx2[15];

        return nmtx;
    }


    /**
     * Multiplies two 4x4 matricies together, and places them
     * in the specified output matrix. The multiplication is done
     * fast, at the expense of code.  The output matrix may not
     * be the same as one of the input matrices or the results
     * are undefined.
     *
     * @param mtx1 The 1st matrix being multiplied.
     * @param mtx2 The 2nd matrix being multiplied.
     * @param dest The output matrix (results go here).  Must not be
     *             one of the input matrices.
     * @return A reference to the output matrix "dest" is returned.
     */
    public static final float[] multiply(float mtx1[], float mtx2[], float dest[]) {

        float m0 = mtx1[0], m1 = mtx1[1], m2 = mtx1[2], m3 = mtx1[3];
        dest[0] = m0 * mtx2[0] + m1 * mtx2[4] + m2 * mtx2[8] + m3 * mtx2[12];
        dest[1] = m0 * mtx2[1] + m1 * mtx2[5] + m2 * mtx2[9] + m3 * mtx2[13];
        dest[2] = m0 * mtx2[2] + m1 * mtx2[6] + m2 * mtx2[10] + m3 * mtx2[14];
        dest[3] = m0 * mtx2[3] + m1 * mtx2[7] + m2 * mtx2[11] + m3 * mtx2[15];

        m0 = mtx1[4];
        m1 = mtx1[5];
        m2 = mtx1[6];
        m3 = mtx1[7];
        dest[4] = m0 * mtx2[0] + m1 * mtx2[4] + m2 * mtx2[8] + m3 * mtx2[12];
        dest[5] = m0 * mtx2[1] + m1 * mtx2[5] + m2 * mtx2[9] + m3 * mtx2[13];
        dest[6] = m0 * mtx2[2] + m1 * mtx2[6] + m2 * mtx2[10] + m3 * mtx2[14];
        dest[7] = m0 * mtx2[3] + m1 * mtx2[7] + m2 * mtx2[11] + m3 * mtx2[15];

        m0 = mtx1[8];
        m1 = mtx1[9];
        m2 = mtx1[10];
        m3 = mtx1[11];
        dest[8] = m0 * mtx2[0] + m1 * mtx2[4] + m2 * mtx2[8] + m3 * mtx2[12];
        dest[9] = m0 * mtx2[1] + m1 * mtx2[5] + m2 * mtx2[9] + m3 * mtx2[13];
        dest[10] = m0 * mtx2[2] + m1 * mtx2[6] + m2 * mtx2[10] + m3 * mtx2[14];
        dest[11] = m0 * mtx2[3] + m1 * mtx2[7] + m2 * mtx2[11] + m3 * mtx2[15];

        m0 = mtx1[12];
        m1 = mtx1[13];
        m2 = mtx1[14];
        m3 = mtx1[15];
        dest[12] = m0 * mtx2[0] + m1 * mtx2[4] + m2 * mtx2[8] + m3 * mtx2[12];
        dest[13] = m0 * mtx2[1] + m1 * mtx2[5] + m2 * mtx2[9] + m3 * mtx2[13];
        dest[14] = m0 * mtx2[2] + m1 * mtx2[6] + m2 * mtx2[10] + m3 * mtx2[14];
        dest[15] = m0 * mtx2[3] + m1 * mtx2[7] + m2 * mtx2[11] + m3 * mtx2[15];

        return dest;
    }

    /****************************************************************
     *	The following are matrix post concatenation routines that	*
     *	can be used as speedy replacements for the more standard	*
     *	matrix mult. routine to generate combined rotations.		*
     ****************************************************************/
    /**
     * Multiply an existing 4x4 transformation matrix by an X-axis
     * rotation matrix quickly.  This is much faster than calling
     * setRotateX() followed by multiply().
     * Equiv. to: T = T*Rx
     * <p/>
     * Rx =  1   0           0         0
     * 0   cos(a)      sin(a)    0
     * 0   -sin(a)     cos(a)    0
     * 0   0           0         1
     *
     * @param angle The rotation angle about the X-axis in radians.
     * @param mtx   The 4x4 matrix to be multiplied by the rotation matrix.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] rotateX(float angle, float mtx[]) {
        float rsin = (float) Math.sin(angle);
        float rcos = (float) Math.cos(angle);

        for (int i = 0; i < 4; i++) {
            int i1 = i * 4 + 1;
            int i2 = i1 + 1;
            float t = mtx[i1];
            mtx[i1] = t * rcos - mtx[i2] * rsin;
            mtx[i2] = t * rsin + mtx[i2] * rcos;
        }

        return mtx;
    }

    /**
     * Multiply an existing 4x4 transformation matrix by a Y-axis
     * rotation matrix quickly.  This is much faster than calling
     * setRotateY() followed by multiply().
     * Equiv. to: T = T*Ry
     * <p/>
     * Ry =  1   0           0         0
     * 0   cos(a)      sin(a)    0
     * 0   -sin(a)     cos(a)    0
     * 0   0           0         1
     *
     * @param angle The rotation angle about the Y-axis in radians.
     * @param mtx   The 4x4 matrix to be multiplied by the rotation matrix.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] rotateY(float angle, float mtx[]) {
        float rsin = (float) Math.sin(angle);
        float rcos = (float) Math.cos(angle);

        for (int i = 0; i < 4; i++) {
            int i0 = i * 4;
            int i2 = i0 + 2;
            float t = mtx[i0];
            mtx[i0] = t * rcos + mtx[i2] * rsin;
            mtx[i2] = mtx[i2] * rcos - t * rsin;
        }

        return mtx;
    }

    /**
     * Multiply an existing 4x4 transformation matrix by a Z-axis
     * rotation matrix quickly.  This is much faster than calling
     * setRotateZ() followed by multiply().
     * Equiv. to: T = T*Rz
     * <p/>
     * Rz =  1   0           0         0
     * 0   cos(a)      sin(a)    0
     * 0   -sin(a)     cos(a)    0
     * 0   0           0         1
     *
     * @param angle The rotation angle about the Y-axis in radians.
     * @param mtx   The 4x4 matrix to be multiplied by the rotation matrix.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] rotateZ(float angle, float mtx[]) {
        float rsin = (float) Math.sin(angle);
        float rcos = (float) Math.cos(angle);

        for (int i = 0; i < 4; i++) {
            int i0 = i * 4;
            int i1 = i0 + 1;
            float t = mtx[i0];
            mtx[i0] = t * rcos - mtx[i1] * rsin;
            mtx[i1] = t * rsin + mtx[i1] * rcos;
        }

        return mtx;
    }

    /**
     * Multiply an existing 4x4 transformation matrix by a set of
     * scale factors quickly.  This is much faster than calling
     * setScale() followed by multiply().
     * Equiv. to: T = T*S
     * <p/>
     * S =   sx  0   0   0
     * 0   sy  0   0
     * 0   0   sz  0
     * 0   0   0   1
     *
     * @param sx  The scale factor in the X-axis direction.
     * @param sy  The scale factor in the Y-axis direction.
     * @param sz  The scale factor in the Z-axis direction.
     * @param mtx The 4x4 matrix to be multiplied by the scale factor matrix.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] scale(float sx, float sy, float sz, float mtx[]) {
        for (int i = 0; i < 4; i++) {
            int i0 = i * 4;
            mtx[i0] *= sx;
            mtx[i0 + 1] *= sy;
            mtx[i0 + 2] *= sz;
        }

        return mtx;
    }

    /**
     * Multiply an existing 4x4 transformation matrix by a
     * translation matrix quickly.  This is much faster than calling
     * setTranslate() followed by multiply().
     * Equiv. to: T = T*Tr
     * <p/>
     * Tr =  1   0   0   Tx
     * 0   1   0   Ty
     * 0   0   1   Tz
     * 0   0   0   1
     *
     * @param tx  The translation amount in the X-axis direction.
     * @param ty  The translation amount in the Y-axis direction.
     * @param tz  The translation amount in the Z-axis direction.
     * @param mtx The 4x4 matrix to be multiplied by the translation matrix.
     * @return A reference to the output matrix "mtx" is returned.
     */
    public static final float[] translate(float tx, float ty, float tz, float mtx[]) {
        for (int i = 0; i < 4; i++) {
            int i0 = i * 4;
            int i3 = i0 + 3;
            mtx[i0] += mtx[i3] * tx;
            mtx[i0 + 1] += mtx[i3] * ty;
            mtx[i0 + 2] += mtx[i3] * tz;
        }

        return mtx;
    }

}
