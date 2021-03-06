package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Futures;
import water.MRTask;
import water.Scope;
import water.TestUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static water.fvec.Vec.makeCon;
import static water.fvec.Vec.makeSeq;

/** This test tests stability of Vec API. */
public class VecTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  /** Test toCategoricalVec call to return correct domain. */
  @Test public void testToCategorical() {
    testToCategoricalDomainMatch(vec(0, 1, 0, 1), ar("0", "1"));
    testToCategoricalDomainMatch(vec(1, 2, 3, 4, 5, 6, 7), ar("1", "2", "3", "4", "5", "6", "7"));
    testToCategoricalDomainMatch(vec(0, 1, 2, 99, 4, 5, 6), ar("0", "1", "2", "4", "5", "6", "99"));
  }

  @Test public void testCalculatingDomainOnNumericalVecReturnsNull() {
    Vec vec = vec(0, 1, 0, 1);
    assertTrue("Should be numerical vector", vec.get_type_str().equals(Vec.TYPE_STR[Vec.T_NUM]));
    String[] domains = vec.domain();
    Assert.assertArrayEquals(null, domains);
    vec.remove();
  }

  @Test public void makeCopy() {
    Vec copyOfVec = null;
    Vec expected = null;
    try {
      Scope.enter();
      Vec originalVec = vec(1, 2, 3, 4, 5);
      copyOfVec = originalVec.makeCopy();
      Scope.untrack(copyOfVec._key);
      Scope.exit();
      expected = vec(1, 2, 3, 4, 5);
      assertVecEquals(expected, copyOfVec, 1e-5);
    } finally {
      if( copyOfVec !=null ) copyOfVec.remove();
      if( expected !=null ) expected.remove();
    }

  }

  private void testToCategoricalDomainMatch(Vec f, String[] expectedDomain) {
    Vec ef = null;
    try {
      ef = f.toCategoricalVec();
      String[] actualDomain = ef.domain();
      Assert.assertArrayEquals("toCategoricalVec call returns wrong domain!", expectedDomain, actualDomain);
    } finally {
      if( f !=null ) f .remove();
      if( ef!=null ) ef.remove();
    }
  }

  @Test public void testMakeConSeq() {
    Vec v;

    v = makeCon(0xCAFE,2*FileVec.DFLT_CHUNK_SIZE,false);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.espc().length == 3);
    assertTrue(
            v.espc()[0] == 0              &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE
    );
    v.remove(new Futures()).blockForPending();

    v = makeCon(0xCAFE,3*FileVec.DFLT_CHUNK_SIZE,false);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(3*FileVec.DFLT_CHUNK_SIZE-1) == 0xCAFE);
    assertTrue(v.espc().length == 4);
    assertTrue(
            v.espc()[0] == 0              &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE   &&
                    v.espc()[2] == FileVec.DFLT_CHUNK_SIZE*2
    );
    v.remove(new Futures()).blockForPending();

    v = makeCon(0xCAFE,3*FileVec.DFLT_CHUNK_SIZE+1,false);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(3*FileVec.DFLT_CHUNK_SIZE) == 0xCAFE);
    assertTrue(v.espc().length == 4);
    assertTrue(
            v.espc()[0] == 0              &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE   &&
                    v.espc()[2] == FileVec.DFLT_CHUNK_SIZE*2 &&
                    v.espc()[3] == FileVec.DFLT_CHUNK_SIZE*3+1
    );
    v.remove(new Futures()).blockForPending();

    v = makeCon(0xCAFE,4*FileVec.DFLT_CHUNK_SIZE,false);
    assertTrue(v.at(234) == 0xCAFE);
    assertTrue(v.at(4*FileVec.DFLT_CHUNK_SIZE-1) == 0xCAFE);
    assertTrue(v.espc().length == 5);
    assertTrue(
            v.espc()[0] == 0              &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE   &&
                    v.espc()[2] == FileVec.DFLT_CHUNK_SIZE*2 &&
                    v.espc()[3] == FileVec.DFLT_CHUNK_SIZE*3
    );
    v.remove(new Futures()).blockForPending();
  }

  @Test public void testMakeSeq() {
    Vec v = makeSeq(3*FileVec.DFLT_CHUNK_SIZE, false);
    assertTrue(v.at(0) == 1);
    assertTrue(v.at(234) == 235);
    assertTrue(v.at(2*FileVec.DFLT_CHUNK_SIZE) == 2*FileVec.DFLT_CHUNK_SIZE+1);
    assertTrue(v.espc().length == 4);
    assertTrue(
            v.espc()[0] == 0 &&
                    v.espc()[1] == FileVec.DFLT_CHUNK_SIZE &&
                    v.espc()[2] == FileVec.DFLT_CHUNK_SIZE * 2
    );
    v.remove(new Futures()).blockForPending();
  }

  @Test public void testMakeConStr() {
    Vec source = makeSeq(2 * FileVec.DFLT_CHUNK_SIZE, false);
    Vec con = source.makeCon(Vec.T_STR);
    // check rollup possible
    assertEquals(0d, con.base(), 0);
    // set each row unique value
    new MRTask() {
      @Override
      public void map(Chunk c) {
        long firstRow = c._vec.espc()[c.cidx()];
        for (int row = 0; row < c._len; row++) {
          c.set(row, "row_" + (firstRow + row));
        }
      }
    }.doAll(con);
    // set row values are correct strings
    for (int row = 0; row < con.length(); row++) {
      assertEquals(row + "th row has expected value", "row_" + row, con.stringAt(row));
    }
    // check rollup possible
    assertEquals(1, con.sparseRatio(), 0);
    source.remove(new Futures()).blockForPending();
    con.remove(new Futures()).blockForPending();
  }
}
