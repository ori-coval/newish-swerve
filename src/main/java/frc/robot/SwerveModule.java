package frc.robot;

import com.ctre.phoenix6.controls.DutyCycleOut;
import com.ctre.phoenix6.controls.VelocityVoltage;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.TalonFX;
import com.revrobotics.CANSparkMax;
import com.revrobotics.SparkMaxPIDController;
import com.revrobotics.CANSparkMax.ControlType;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;

import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.Timer;
import frc.lib.math.Conversions;
import frc.lib.util.CTREModuleState;
import frc.lib.util.SwerveModuleConstants;
import frc.robot.CANSparkMaxUtil.Usage;

public class SwerveModule {
    public int moduleNumber;
    private Rotation2d angleOffset;
    private Rotation2d lastAngle;

    private CANSparkMax mAngleMotor;
    private TalonFX mDriveMotor;
    private CANcoder angleEncoder;

    private final SimpleMotorFeedforward driveFeedForward = new SimpleMotorFeedforward(Constants.Swerve.driveKS, Constants.Swerve.driveKV, Constants.Swerve.driveKA);

    /* drive motor control requests */
    private final DutyCycleOut driveDutyCycle = new DutyCycleOut(0);
    private final VelocityVoltage driveVelocity = new VelocityVoltage(0);

    /* angle motor control requests */
    private final SparkMaxPIDController anglePosition;

    public SwerveModule(int moduleNumber, SwerveModuleConstants moduleConstants){
        this.moduleNumber = moduleNumber;
        this.angleOffset = moduleConstants.angleOffset;
        
        /* Angle Encoder Config */
        angleEncoder = new CANcoder(moduleConstants.cancoderID);
        configAngleEncoder();

        /* Angle Motor Config */
        mAngleMotor = new CANSparkMax(moduleConstants.angleMotorID, MotorType.kBrushless);
        anglePosition = mAngleMotor.getPIDController();
        configAngleMotor();

        /* Drive Motor Config */
        mDriveMotor = new TalonFX(moduleConstants.driveMotorID);
        configDriveMotor();

        lastAngle = getState().angle;
    }

    public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop){
        /* This is a custom optimize function, since default WPILib optimize assumes continuous controller which CTRE and Rev onboard is not */
        desiredState = CTREModuleState.optimize(desiredState, getState().angle);
        setAngle(desiredState);
        setSpeed(desiredState, isOpenLoop);
    }

    private void setSpeed(SwerveModuleState desiredState, boolean isOpenLoop){
        if(isOpenLoop){
            driveDutyCycle.Output = desiredState.speedMetersPerSecond / Constants.Swerve.maxSpeed;
            mDriveMotor.setControl(driveDutyCycle);
        }
        else {
            driveVelocity.Velocity = Conversions.MPSToTalon(desiredState.speedMetersPerSecond, Constants.Swerve.wheelCircumference, Constants.Swerve.driveGearRatio);
            driveVelocity.FeedForward = driveFeedForward.calculate(desiredState.speedMetersPerSecond);
            mDriveMotor.setControl(driveVelocity);
        }
    }
    

    private void setAngle(SwerveModuleState desiredState){
        // Prevent rotating module if speed is less then 1%. Prevents jittering.
        Rotation2d angle = (Math.abs(desiredState.speedMetersPerSecond) <= (Constants.Swerve.maxSpeed * 0.01))
                ? lastAngle
                : desiredState.angle;
        
        anglePosition.setReference(angle.getDegrees(), ControlType.kPosition);
        lastAngle = angle;
    }

    private Rotation2d getAngle(){
        return Rotation2d.fromDegrees(Conversions.talonToDegrees(mAngleMotor.getEncoder().getPosition(), Constants.Swerve.angleGearRatio));
    }

    public Rotation2d getCANcoder(){
        return Rotation2d.fromRotations(angleEncoder.getAbsolutePosition().getValue());
    }

    private Rotation2d waitForCANcoder(){
        /* wait for up to 250ms for a new CANcoder position */
        return Rotation2d.fromRotations(angleEncoder.getAbsolutePosition().waitForUpdate(250).getValue());
    }

    public void resetToAbsolute(){
        double absolutePosition = Conversions.degreesToTalon(waitForCANcoder().getDegrees() - angleOffset.getDegrees(), Constants.Swerve.angleGearRatio);
        mAngleMotor.getEncoder().setPosition(absolutePosition);
    }

    private void configAngleEncoder(){    
        angleEncoder.getConfigurator().apply(Robot.ctreConfigs.swerveCANcoderConfig);
    }

    private void configAngleMotor(){
        mAngleMotor.restoreFactoryDefaults();
        CANSparkMaxUtil.setCANSparkMaxBusUsage(mAngleMotor, Usage.kPositionOnly);
        mAngleMotor.setSmartCurrentLimit(Constants.Swerve.angleCurrentLimit);
        mAngleMotor.setInverted(Constants.Swerve.angleMotorInvert);
        mAngleMotor.setIdleMode(Constants.Swerve.angleNeutralMode);
        mAngleMotor.getEncoder().setPositionConversionFactor(Constants.Swerve.angleGearRatio);
        anglePosition.setP(Constants.Swerve.angleKP);
        anglePosition.setI(Constants.Swerve.angleKI);
        anglePosition.setD(Constants.Swerve.angleKD);
        anglePosition.setFF(Constants.Swerve.angleKFF);
        mAngleMotor.enableVoltageCompensation(Constants.Swerve.voltageComp);
        mAngleMotor.burnFlash();
        Timer.delay(1);
        resetToAbsolute();
    }

    private void configDriveMotor(){
        mDriveMotor.getConfigurator().apply(Robot.ctreConfigs.swerveDriveFXConfig);
        mDriveMotor.getConfigurator().setPosition(0);
    }

    public SwerveModuleState getState(){
        return new SwerveModuleState(
            Conversions.talonToMPS(mDriveMotor.getVelocity().getValue(), Constants.Swerve.wheelCircumference, Constants.Swerve.driveGearRatio), 
            getAngle()
        ); 
    }

    public SwerveModulePosition getPosition(){
        return new SwerveModulePosition(
            Conversions.talonToMeters(mDriveMotor.getPosition().getValue(), Constants.Swerve.wheelCircumference, Constants.Swerve.driveGearRatio), 
            getAngle()
        );
    }
}