/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    ClassifierSplitEvaluator.java
 *    Copyright (C) 1999 Len Trigg
 *
 */


package weka.experiment;

import java.io.*;
import java.util.*;

import weka.core.*;
import weka.classifiers.*;
import weka.classifiers.rules.ZeroR;


/**
 * A SplitEvaluator that produces results for a classification scheme
 * on a nominal class attribute.
 *
 * -W classname <br>
 * Specify the full class name of the classifier to evaluate. <p>
 *
 * -C class index <br>
 * The index of the class for which IR statistics are to
 * be output. (default 1) <p>
 *
 * -I attr index <br>
 * The index of an attribute to output in the tresults. This 
 * attribute should identify an instance in order to know 
 * which instances are tested in a fold (default 1).
 *
 * -P 
 * Add the prediction and target columns to the result file for each fold.
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @version $Revision: 1.1 $
 */
public class ClassifierSplitEvaluator implements SplitEvaluator, 
  OptionHandler, AdditionalMeasureProducer {
  
  /** The template classifier */
  protected Classifier m_Template = new ZeroR();

  /** The classifier used for evaluation */
  protected Classifier m_Classifier;

  /** The names of any additional measures to look for in SplitEvaluators */
  protected String [] m_AdditionalMeasures = null;

  /** Array of booleans corresponding to the measures in m_AdditionalMeasures
      indicating which of the AdditionalMeasures the current classifier
      can produce */
  protected boolean [] m_doesProduce = null;

  /** The number of additional measures that need to be filled in
      after taking into account column constraints imposed by the final
      destination for results */
  protected int m_numberAdditionalMeasures = 0;

  /** Holds the statistics for the most recent application of the classifier */
  protected String m_result = null;

  /** The classifier options (if any) */
  protected String m_ClassifierOptions = "";

  /** The classifier version */
  protected String m_ClassifierVersion = "";

  /** The length of a key */
  private static final int KEY_SIZE = 3;

  /** The length of a result */
  private static final int RESULT_SIZE = 25;

  /** The number of IR statistics */
  private static final int NUM_IR_STATISTICS = 11;
  
  /** Class index for information retrieval statistics (default 0) */
  private int m_IRclass = 0;
  
  /** Flag for prediction and target columns output.*/
  private boolean m_predTargetColumn = false;

  /** Attribute index of instance identifier (default -1) */
  private int m_attID = -1;
  
  /**
   * No args constructor.
   */
  public ClassifierSplitEvaluator() {

    updateOptions();
  }

  /**
   * Returns a string describing this split evaluator
   * @return a description of the split evaluator suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {
    return " A SplitEvaluator that produces results for a classification "
      +"scheme on a nominal class attribute.";
  }

  /**
   * Returns an enumeration describing the available options..
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {

    Vector newVector = new Vector(4);

    newVector.addElement(new Option(
	     "\tThe full class name of the classifier.\n"
	      +"\teg: weka.classifiers.bayes.NaiveBayes", 
	     "W", 1, 
	     "-W <class name>"));
    newVector.addElement(new Option(
	     "\tThe index of the class for which IR statistics\n" +
	     "\tare to be output. (default 1)",
	     "C", 1, 
	     "-C <index>"));
    newVector.addElement(new Option(
	     "\tThe index of an attribute to output in the\n" +
	     "\tresults. This attribute should identify an\n" +
             "\tinstance in order to know which instances are\n" +
             "\tin the test set of a cross validation. if 0\n" +
             "\tno output (default 0).",
	     "I", 1, 
	     "-I <index>"));
    newVector.addElement(new Option(
	     "\tAdd target and prediction columns to the result\n" +
             "\tfor each fold.",
	     "P", 0, 
	     "-P"));

    if ((m_Template != null) &&
	(m_Template instanceof OptionHandler)) {
      newVector.addElement(new Option(
	     "",
	     "", 0, "\nOptions specific to classifier "
	     + m_Template.getClass().getName() + ":"));
      Enumeration enu = ((OptionHandler)m_Template).listOptions();
      while (enu.hasMoreElements()) {
	newVector.addElement(enu.nextElement());
      }
    }
    return newVector.elements();
  }

  /**
   * Parses a given list of options. Valid options are:<p>
   *
   * -W classname <br>
   * Specify the full class name of the classifier to evaluate. <p>
   *
   * -C class index <br>
   * The index of the class for which IR statistics are to
   * be output. (default 1) <p>
   *
   * -I attr index <br>
   * The index of an attribute to output in the tresults. This 
   * attribute should identify an instance in order to know 
   * which instances are tested in a fold. if zero, no output (default 0).
   *
   * -P 
   * The flag that indicate if the prediction and targets have to be output
   * in the result files for each fold.
   *
   * All option after -- will be passed to the classifier.
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {
    
    String cName = Utils.getOption('W', options);
    if (cName.length() == 0) {
      throw new Exception("A classifier must be specified with"
			  + " the -W option.");
    }
    // Do it first without options, so if an exception is thrown during
    // the option setting, listOptions will contain options for the actual
    // Classifier.
    setClassifier(Classifier.forName(cName, null));
    if (getClassifier() instanceof OptionHandler) {
      ((OptionHandler) getClassifier())
	.setOptions(Utils.partitionOptions(options));
      updateOptions();
    }

    String indexName = Utils.getOption('C', options);
    if (indexName.length() != 0) {
      m_IRclass = (new Integer(indexName)).intValue() - 1;
    } else {
      m_IRclass = 0;
    }

    String attID = Utils.getOption('I', options);
    if (attID.length() != 0) {
      m_attID = (new Integer(attID)).intValue() - 1;
    } else {
      m_attID = -1;
    }
    
    m_predTargetColumn = Utils.getFlag('P', options);
  }

  /**
   * Gets the current settings of the Classifier.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String [] getOptions() {

    String [] classifierOptions = new String [0];
    if ((m_Template != null) && 
	(m_Template instanceof OptionHandler)) {
      classifierOptions = ((OptionHandler)m_Template).getOptions();
    }
    
    String [] options = new String [classifierOptions.length + 8];
    int current = 0;

    if (getClassifier() != null) {
      options[current++] = "-W";
      options[current++] = getClassifier().getClass().getName();
    }
    options[current++] = "-I"; 
    options[current++] = "" + (m_attID + 1);

    if (getPredTargetColumn()) options[current++] = "-P";
    
    options[current++] = "-C"; 
    options[current++] = "" + (m_IRclass + 1);
    options[current++] = "--";
    
    System.arraycopy(classifierOptions, 0, options, current, 
		     classifierOptions.length);
    current += classifierOptions.length;
    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }

  /**
   * Set a list of method names for additional measures to look for
   * in Classifiers. This could contain many measures (of which only a
   * subset may be produceable by the current Classifier) if an experiment
   * is the type that iterates over a set of properties.
   * @param additionalMeasures a list of method names
   */
  public void setAdditionalMeasures(String [] additionalMeasures) {
    // System.err.println("ClassifierSplitEvaluator: setting additional measures");
    m_AdditionalMeasures = additionalMeasures;
    
    // determine which (if any) of the additional measures this classifier
    // can produce
    if (m_AdditionalMeasures != null && m_AdditionalMeasures.length > 0) {
      m_doesProduce = new boolean [m_AdditionalMeasures.length];

      if (m_Template instanceof AdditionalMeasureProducer) {
	Enumeration en = ((AdditionalMeasureProducer)m_Template).
	  enumerateMeasures();
	while (en.hasMoreElements()) {
	  String mname = (String)en.nextElement();
	  for (int j=0;j<m_AdditionalMeasures.length;j++) {
	    if (mname.compareToIgnoreCase(m_AdditionalMeasures[j]) == 0) {
	      m_doesProduce[j] = true;
	    }
	  }
	}
      }
    } else {
      m_doesProduce = null;
    }
  }

  /**
   * Returns an enumeration of any additional measure names that might be
   * in the classifier
   * @return an enumeration of the measure names
   */
  public Enumeration enumerateMeasures() {
    Vector newVector = new Vector();
    if (m_Template instanceof AdditionalMeasureProducer) {
      Enumeration en = ((AdditionalMeasureProducer)m_Template).
	enumerateMeasures();
      while (en.hasMoreElements()) {
	String mname = (String)en.nextElement();
	newVector.addElement(mname);
      }
    }
    return newVector.elements();
  }
  
  /**
   * Returns the value of the named measure
   * @param measureName the name of the measure to query for its value
   * @return the value of the named measure
   * @exception IllegalArgumentException if the named measure is not supported
   */
  public double getMeasure(String additionalMeasureName) {
    if (m_Template instanceof AdditionalMeasureProducer) {
      if (m_Classifier == null) {
	throw new IllegalArgumentException("ClassifierSplitEvaluator: " +
					   "Can't return result for measure, " +
					   "classifier has not been built yet.");
      }
      return ((AdditionalMeasureProducer)m_Classifier).
	getMeasure(additionalMeasureName);
    } else {
      throw new IllegalArgumentException("ClassifierSplitEvaluator: "
			  +"Can't return value for : "+additionalMeasureName
			  +". "+m_Template.getClass().getName()+" "
			  +"is not an AdditionalMeasureProducer");
    }
  }

  /**
   * Gets the data types of each of the key columns produced for a single run.
   * The number of key fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array containing objects of the type of each key column. The 
   * objects should be Strings, or Doubles.
   */
  public Object [] getKeyTypes() {

    Object [] keyTypes = new Object[KEY_SIZE];
    keyTypes[0] = "";
    keyTypes[1] = "";
    keyTypes[2] = "";
    return keyTypes;
  }

  /**
   * Gets the names of each of the key columns produced for a single run.
   * The number of key fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array containing the name of each key column
   */
  public String [] getKeyNames() {

    String [] keyNames = new String[KEY_SIZE];
    keyNames[0] = "Scheme";
    keyNames[1] = "Scheme_options";
    keyNames[2] = "Scheme_version_ID";
    return keyNames;
  }

  /**
   * Gets the key describing the current SplitEvaluator. For example
   * This may contain the name of the classifier used for classifier
   * predictive evaluation. The number of key fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array of objects containing the key.
   */
  public Object [] getKey(){

    Object [] key = new Object[KEY_SIZE];
    key[0] = m_Template.getClass().getName();
    key[1] = m_ClassifierOptions;
    key[2] = m_ClassifierVersion;
    return key;
  }

  /**
   * Gets the data types of each of the result columns produced for a 
   * single run. The number of result fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array containing objects of the type of each result column. 
   * The objects should be Strings, or Doubles.
   */
  public Object [] getResultTypes() {
    int addm = (m_AdditionalMeasures != null) 
      ? m_AdditionalMeasures.length 
      : 0;
    int overall_length = RESULT_SIZE+addm;
    overall_length += NUM_IR_STATISTICS;
    if (getAttributeID() >= 0) overall_length += 1;
    if (getPredTargetColumn()) overall_length += 2;
    Object [] resultTypes = new Object[overall_length];
    Double doub = new Double(0);
    int current = 0;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    // IR stats
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    // Timing stats
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    // ID/Targets/Predictions
    if (getAttributeID() >= 0) resultTypes[current++] = "";
    if (getPredTargetColumn()){
        resultTypes[current++] = "";
        resultTypes[current++] = "";
    }
    
    // Classifier defined extras
    resultTypes[current++] = "";

    // add any additional measures
    for (int i=0;i<addm;i++) {
      resultTypes[current++] = doub;
    }
    if (current != overall_length) {
      throw new Error("ResultTypes didn't fit RESULT_SIZE");
    }
    return resultTypes;
  }

  /**
   * Gets the names of each of the result columns produced for a single run.
   * The number of result fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array containing the name of each result column
   */
  public String [] getResultNames() {
    int addm = (m_AdditionalMeasures != null) 
      ? m_AdditionalMeasures.length 
      : 0;
    int overall_length = RESULT_SIZE+addm;
    overall_length += NUM_IR_STATISTICS;
    if (getAttributeID() >= 0) overall_length += 1;
    if (getPredTargetColumn()) overall_length += 2;

    String [] resultNames = new String[overall_length];
    int current = 0;
    resultNames[current++] = "Number_of_training_instances";
    resultNames[current++] = "Number_of_testing_instances";

    // Basic performance stats - right vs wrong
    resultNames[current++] = "Number_correct";
    resultNames[current++] = "Number_incorrect";
    resultNames[current++] = "Number_unclassified";
    resultNames[current++] = "Percent_correct";
    resultNames[current++] = "Percent_incorrect";
    resultNames[current++] = "Percent_unclassified";
    resultNames[current++] = "Kappa_statistic";

    // Sensitive stats - certainty of predictions
    resultNames[current++] = "Mean_absolute_error";
    resultNames[current++] = "Root_mean_squared_error";
    resultNames[current++] = "Relative_absolute_error";
    resultNames[current++] = "Root_relative_squared_error";

    // SF stats
    resultNames[current++] = "SF_prior_entropy";
    resultNames[current++] = "SF_scheme_entropy";
    resultNames[current++] = "SF_entropy_gain";
    resultNames[current++] = "SF_mean_prior_entropy";
    resultNames[current++] = "SF_mean_scheme_entropy";
    resultNames[current++] = "SF_mean_entropy_gain";

    // K&B stats
    resultNames[current++] = "KB_information";
    resultNames[current++] = "KB_mean_information";
    resultNames[current++] = "KB_relative_information";

    // IR stats
    resultNames[current++] = "True_positive_rate";
    resultNames[current++] = "Num_true_positives";
    resultNames[current++] = "False_positive_rate";
    resultNames[current++] = "Num_false_positives";
    resultNames[current++] = "True_negative_rate";
    resultNames[current++] = "Num_true_negatives";
    resultNames[current++] = "False_negative_rate";
    resultNames[current++] = "Num_false_negatives";
    resultNames[current++] = "IR_precision";
    resultNames[current++] = "IR_recall";
    resultNames[current++] = "F_measure";

    // Timing stats
    resultNames[current++] = "Time_training";
    resultNames[current++] = "Time_testing";

    // ID/Targets/Predictions
    if (getAttributeID() >= 0) resultNames[current++] = "Instance_ID";
    if (getPredTargetColumn()){
        resultNames[current++] = "Targets";
        resultNames[current++] = "Predictions";
    }
    
    // Classifier defined extras
    resultNames[current++] = "Summary";
    // add any additional measures
    for (int i=0;i<addm;i++) {
      resultNames[current++] = m_AdditionalMeasures[i];
    }
    if (current != overall_length) {
      throw new Error("ResultNames didn't fit RESULT_SIZE");
    }
    return resultNames;
  }

  /**
   * Gets the results for the supplied train and test datasets. Now performs
   * a deep copy of the classifier before it is built and evaluated (just in case
   * the classifier is not initialized properly in buildClassifier()).
   *
   * @param train the training Instances.
   * @param test the testing Instances.
   * @return the results stored in an array. The objects stored in
   * the array may be Strings, Doubles, or null (for the missing value).
   * @exception Exception if a problem occurs while getting the results
   */
  public Object [] getResult(Instances train, Instances test) 
    throws Exception {
    
    if (train.classAttribute().type() != Attribute.NOMINAL) {
      throw new Exception("Class attribute is not nominal!");
    }
    if (m_Template == null) {
      throw new Exception("No classifier has been specified");
    }
    int addm = (m_AdditionalMeasures != null) 
      ? m_AdditionalMeasures.length 
      : 0;
    int overall_length = RESULT_SIZE+addm;
    overall_length += NUM_IR_STATISTICS;
    if (getAttributeID() >= 0) overall_length += 1;
    if (getPredTargetColumn()) overall_length += 2;

    Object [] result = new Object[overall_length];
    Evaluation eval = new Evaluation(train);
    m_Classifier = Classifier.makeCopy(m_Template);
    long trainTimeStart = System.currentTimeMillis();
    m_Classifier.buildClassifier(train);
    long trainTimeElapsed = System.currentTimeMillis() - trainTimeStart;
    long testTimeStart = System.currentTimeMillis();
    double predictions[] = eval.evaluateModel(m_Classifier, test);
    long testTimeElapsed = System.currentTimeMillis() - testTimeStart;
    m_result = eval.toSummaryString();
    // The results stored are all per instance -- can be multiplied by the
    // number of instances to get absolute numbers
    int current = 0;
    result[current++] = new Double(train.numInstances());
    result[current++] = new Double(eval.numInstances());
    result[current++] = new Double(eval.correct());
    result[current++] = new Double(eval.incorrect());
    result[current++] = new Double(eval.unclassified());
    result[current++] = new Double(eval.pctCorrect());
    result[current++] = new Double(eval.pctIncorrect());
    result[current++] = new Double(eval.pctUnclassified());
    result[current++] = new Double(eval.kappa());

    result[current++] = new Double(eval.meanAbsoluteError());
    result[current++] = new Double(eval.rootMeanSquaredError());
    result[current++] = new Double(eval.relativeAbsoluteError());
    result[current++] = new Double(eval.rootRelativeSquaredError());

    result[current++] = new Double(eval.SFPriorEntropy());
    result[current++] = new Double(eval.SFSchemeEntropy());
    result[current++] = new Double(eval.SFEntropyGain());
    result[current++] = new Double(eval.SFMeanPriorEntropy());
    result[current++] = new Double(eval.SFMeanSchemeEntropy());
    result[current++] = new Double(eval.SFMeanEntropyGain());

    // K&B stats
    result[current++] = new Double(eval.KBInformation());
    result[current++] = new Double(eval.KBMeanInformation());
    result[current++] = new Double(eval.KBRelativeInformation());

    // IR stats
    result[current++] = new Double(eval.truePositiveRate(m_IRclass));
    result[current++] = new Double(eval.numTruePositives(m_IRclass));
    result[current++] = new Double(eval.falsePositiveRate(m_IRclass));
    result[current++] = new Double(eval.numFalsePositives(m_IRclass));
    result[current++] = new Double(eval.trueNegativeRate(m_IRclass));
    result[current++] = new Double(eval.numTrueNegatives(m_IRclass));
    result[current++] = new Double(eval.falseNegativeRate(m_IRclass));
    result[current++] = new Double(eval.numFalseNegatives(m_IRclass));
    result[current++] = new Double(eval.precision(m_IRclass));
    result[current++] = new Double(eval.recall(m_IRclass));
    result[current++] = new Double(eval.fMeasure(m_IRclass));

    // Timing stats
    result[current++] = new Double(trainTimeElapsed / 1000.0);
    result[current++] = new Double(testTimeElapsed / 1000.0);

    // IDs
    if (getAttributeID() >= 0){
        String idsString = "";
        if (test.attribute(m_attID).isNumeric()){
            if (test.numInstances() > 0)
                idsString += test.instance(0).value(m_attID);
            for(int i=1;i<test.numInstances();i++){
                idsString += "|" + test.instance(i).value(m_attID);
            }
        } else {
            if (test.numInstances() > 0)
                idsString += test.instance(0).stringValue(m_attID);
            for(int i=1;i<test.numInstances();i++){
                idsString += "|" + test.instance(i).stringValue(m_attID);
            }
        }
        result[current++] = idsString;
    }
    
    if (getPredTargetColumn()){
        if (test.classAttribute().isNumeric()){
            // Targets
            if (test.numInstances() > 0){
                String targetsString = "";
                targetsString += test.instance(0).value(test.classIndex());
                for(int i=1;i<test.numInstances();i++){
                    targetsString += "|" + test.instance(i).value(test.classIndex());
                }
                result[current++] = targetsString;
            }
    
            // Predictions
            if (predictions.length > 0){ 
                String predictionsString = "";
                predictionsString += predictions[0];
                for(int i=1;i<predictions.length;i++){
                    predictionsString += "|" + predictions[i];
                }
                result[current++] = predictionsString;
            }            
        } else {
            // Targets
            if (test.numInstances() > 0){
                String targetsString = "";
                targetsString += test.instance(0).stringValue(test.classIndex());
                for(int i=1;i<test.numInstances();i++){
                    targetsString += "|" + test.instance(i).stringValue(test.classIndex());
                }
                result[current++] = targetsString;
            }
    
            // Predictions
            if (predictions.length > 0){ 
                String predictionsString = "";
                predictionsString += test.classAttribute().value((int) predictions[0]);
                for(int i=1;i<predictions.length;i++){
                    predictionsString += "|" + test.classAttribute().value((int) predictions[i]);
                }
                result[current++] = predictionsString;
            }
        }
    }
    
    if (m_Classifier instanceof Summarizable) {
      result[current++] = ((Summarizable)m_Classifier).toSummaryString();
    } else {
      result[current++] = null;
    }

    for (int i=0;i<addm;i++) {
      if (m_doesProduce[i]) {
	try {
	  double dv = ((AdditionalMeasureProducer)m_Classifier).
	    getMeasure(m_AdditionalMeasures[i]);
	  if (!Instance.isMissingValue(dv)) {
	    Double value = new Double(dv);
	    result[current++] = value;
	  } else {
	    result[current++] = null;
	  }
	} catch (Exception ex) {
	  System.err.println(ex);
	}
      } else {
	result[current++] = null;
      }
    }

    if (current != overall_length) {
      throw new Error("Results didn't fit RESULT_SIZE");
    }
    return result;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String classifierTipText() {
    return "The classifier to use.";
  }

  /**
   * Get the value of Classifier.
   *
   * @return Value of Classifier.
   */
  public Classifier getClassifier() {
    
    return m_Template;
  }
  
  /**
   * Sets the classifier.
   *
   * @param newClassifier the new classifier to use.
   */
  public void setClassifier(Classifier newClassifier) {
    
    m_Template = newClassifier;
    updateOptions();
  }
  
  /**
   * Get the value of ClassForIRStatistics.
   * @return Value of ClassForIRStatistics.
   */
  public int getClassForIRStatistics() {
    return m_IRclass;
  }
  
  /**
   * Set the value of ClassForIRStatistics.
   * @param v  Value to assign to ClassForIRStatistics.
   */
  public void setClassForIRStatistics(int v) {
    m_IRclass = v;
  }

  /**
   * Get the index of Attibute Identifying the instances
   * @return index of outputed Attribute.
   */
  public int getAttributeID() {
    return m_attID;
  }
  
  /**
   * Set the index of Attibute Identifying the instances
   * @param v index the attribute to output
   */
  public void setAttributeID(int v) {
    m_attID = v;
  }
    
  /**
   *@return true if the prediction and target columns must be outputed.
   */
  public boolean getPredTargetColumn(){
      return m_predTargetColumn;
  }

  /**
   * Set the flag for prediction and target output.
   *@param v true if the 2 columns have to be outputed. false otherwise.
   */
  public void setPredTargetColumn(boolean v){
      m_predTargetColumn = v;
  }
  
  /**
   * Updates the options that the current classifier is using.
   */
  protected void updateOptions() {
    
    if (m_Template instanceof OptionHandler) {
      m_ClassifierOptions = Utils.joinOptions(((OptionHandler)m_Template)
					      .getOptions());
    } else {
      m_ClassifierOptions = "";
    }
    if (m_Template instanceof Serializable) {
      ObjectStreamClass obs = ObjectStreamClass.lookup(m_Template
						       .getClass());
      m_ClassifierVersion = "" + obs.getSerialVersionUID();
    } else {
      m_ClassifierVersion = "";
    }
  }

  /**
   * Set the Classifier to use, given it's class name. A new classifier will be
   * instantiated.
   *
   * @param newClassifier the Classifier class name.
   * @exception Exception if the class name is invalid.
   */
  public void setClassifierName(String newClassifierName) throws Exception {

    try {
      setClassifier((Classifier)Class.forName(newClassifierName)
		    .newInstance());
    } catch (Exception ex) {
      throw new Exception("Can't find Classifier with class name: "
			  + newClassifierName);
    }
  }

  /**
   * Gets the raw output from the classifier
   * @return the raw output from the classifier
   */
  public String getRawResultOutput() {
    StringBuffer result = new StringBuffer();

    if (m_Classifier == null) {
      return "<null> classifier";
    }
    result.append(toString());
    result.append("Classifier model: \n"+m_Classifier.toString()+'\n');

    // append the performance statistics
    if (m_result != null) {
      result.append(m_result);
      
      if (m_doesProduce != null) {
	for (int i=0;i<m_doesProduce.length;i++) {
	  if (m_doesProduce[i]) {
	    try {
	      double dv = ((AdditionalMeasureProducer)m_Classifier).
		getMeasure(m_AdditionalMeasures[i]);
	      if (!Instance.isMissingValue(dv)) {
		Double value = new Double(dv);
		result.append(m_AdditionalMeasures[i]+" : "+value+'\n');
	      } else {
		result.append(m_AdditionalMeasures[i]+" : "+'?'+'\n');
	      }
	    } catch (Exception ex) {
	      System.err.println(ex);
	    }
	  } 
	}
      }
    }
    return result.toString();
  }

  /**
   * Returns a text description of the split evaluator.
   *
   * @return a text description of the split evaluator.
   */
  public String toString() {

    String result = "ClassifierSplitEvaluator: ";
    if (m_Template == null) {
      return result + "<null> classifier";
    }
    return result + m_Template.getClass().getName() + " " 
      + m_ClassifierOptions + "(version " + m_ClassifierVersion + ")";
  }
} // ClassifierSplitEvaluator
