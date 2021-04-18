/**
 * Double Pendulum Simulation.
 * reference: https://www.myphysicslab.com/pendulum/double-pendulum-en.html
 */

#define G $G_CONSTANT$
#define DT $DT_CONSTANT$

__kernel void pendulum(
    __global float *length1, __global float *length2,
    __global float *mass1,   __global float *mass2,
    __global float *theta1,  __global float *theta2,
    __global float *omega1,  __global float *omega2
) {
    int xid = get_global_id(0);
    float currentLength1 = length1[xid];
    float currentLength2 = length2[xid];
    float currentMass1 = mass1[xid];
    float currentMass2 = mass2[xid];
    float currentTheta1 = theta1[xid];
    float currentTheta2 = theta2[xid];
    float currentOmega1 = omega1[xid];
    float currentOmega2 = omega2[xid];

    // calculate acceleration
    // common part of denominator
    float denominator = 2 * currentMass1 + currentMass2 - currentMass2 * cos(2 * currentTheta1 - 2 * currentTheta2);

    // for acc1
    // numerator part1: −g (2 m1 + m2) sin θ1
    float acc1NumeratorPart1 = - G * (2 * currentMass1 + currentMass2) * sin(currentTheta1);
    // numerator part2: − m2 g sin(θ1 − 2 θ2)
    float acc1NumeratorPart2 = - currentMass2 * G * sin(currentTheta1 - 2 * currentTheta2);
    // numerator part3: − 2 sin(θ1 − θ2) m2
    float acc1NumeratorPart3 = - 2.0 * sin(currentTheta1 - currentTheta2) * currentMass2;
    // numerator part4: θ2'2 L2 + θ1'2 L1 cos(θ1 − θ2)
    float acc1NumeratorPart4 = currentOmega2 * currentOmega2 * currentLength2 + currentOmega1 * currentOmega1 * currentLength1 * cos(currentTheta1 - currentTheta2);
    // acc1
    float acc1 = (acc1NumeratorPart1 + acc1NumeratorPart2 + acc1NumeratorPart3 * acc1NumeratorPart4) / (currentLength1 * denominator);

    // acc2
    // numerator part1: 2 sin(θ1 − θ2)
    float acc2NumeratorPart1 = 2 * sin(currentTheta1 - currentTheta2);
    // numerator part2: θ1'2 L1 (m1 + m2)
    float acc2NumeratorPart2 = currentOmega1 * currentOmega1 * currentLength1 * (currentMass1 + currentMass2);
    // numerator part3: g(m1 + m2) cos θ1
    float acc2NumeratorPart3 = G * (currentMass1 + currentMass2) * cos(currentTheta1);
    // numerator part4: θ2'2 L2 m2 cos(θ1 − θ2)
    float acc2NumeratorPart4 = currentOmega2 * currentOmega2 * currentLength2 * currentMass2 * cos(currentTheta1 - currentTheta2);
    // acc2
    float acc2 = (acc2NumeratorPart1 * (acc2NumeratorPart2 + acc2NumeratorPart3 + acc2NumeratorPart4)) / (currentLength2 * denominator);


    // update current angular velocity
    // omega1
    float deltaOmega1 = acc1 * DT;
    omega1[xid] = currentOmega1 + deltaOmega1;
    currentOmega1 += deltaOmega1;

    // omega2
    float deltaOmega2 = acc2 * DT;
    omega2[xid] = currentOmega2 + deltaOmega2;
    currentOmega2 += deltaOmega2;


    // update current theta
    // theta1
    float DTheta1 = currentOmega1 * DT;
    theta1[xid] = currentTheta1 + DTheta1;

    // theta2
    float DTheta2 = currentOmega2 * DT;
    theta2[xid] = currentTheta2 + DTheta2;
}