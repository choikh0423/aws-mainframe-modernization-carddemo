import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';
import { getUser, deleteUser } from '../../api/users';

/**
 * CU03 — Delete User (COUSR03C / map COUSR3A):
 *   - ENTER with a User ID -> fetch and paint the record read-only, then
 *     "Press PF5 key to delete this user ..." (FR-UD-3, cbl:281-286)
 *   - an unknown id -> "User ID NOT found..." (FR-UD-4, cbl:287-293)
 *   - PF5 -> delete immediately, with no confirmation and no protection for the
 *     signed-on or last administrator (FR-UD-7, FR-UD-14, quirk Q7), then
 *     "User <id> has been deleted ..." (cbl:313-322)
 *   - PF3 -> COADM01C (FR-UD-11); PF4 -> clear (FR-UD-12); PF12 -> COADM01C
 *     (FR-UD-13, cbl:132-135)
 * The map has no password field, and First/Last/Type are ASKIP output fields.
 */

const DELETE_PROMPT = 'Press PF5 key to delete this user ...';
const PF_KEYS = 'ENTER=Fetch  F3=Back  F4=Clear  F5=Delete';

const EMPTY_DATA = { firstName: '', lastName: '', userType: '' };

const styles = {
  form: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '16px' },
  row: { marginBottom: '8px' },
  label: { display: 'inline-block', width: '160px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    width: '220px',
    textTransform: 'uppercase',
  },
  hint: { marginLeft: '10px', color: '#12321a' },
  keys: { marginTop: '12px', display: 'flex', gap: '8px', flexWrap: 'wrap' },
  button: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
  error: {
    color: '#b00020',
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
  success: {
    color: '#0b6b18',
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
};

const readOnly = { ...styles.input, background: '#eef7ee', color: '#12321a' };

export default function DeleteUserPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const selectedId = params.get('id') || '';

  const [userId, setUserId] = useState(selectedId);
  const [data, setData] = useState(EMPTY_DATA);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fetchUser = useCallback(async (id) => {
    setError('');
    setSuccess('');
    try {
      const record = await getUser(id);
      setData({
        firstName: record.firstName,
        lastName: record.lastName,
        userType: record.userType,
      });
      setLoaded(true);
      setSuccess(DELETE_PROMPT);
    } catch (err) {
      setData(EMPTY_DATA);
      setLoaded(false);
      setError(err.message);
    }
  }, []);

  // Entry from CU00 with a selected row fetches straight away (cbl:104-113).
  useEffect(() => {
    if (selectedId) {
      fetchUser(selectedId);
    }
  }, [selectedId, fetchUser]);

  const onEnter = useCallback((e) => {
    if (e) {
      e.preventDefault();
    }
    fetchUser(userId);
  }, [userId, fetchUser]);

  const remove = useCallback(async () => {
    setError('');
    setSuccess('');
    try {
      const result = await deleteUser(userId);
      // INITIALIZE-ALL-FIELDS after a successful DELETE (cbl:313-316).
      setUserId('');
      setData(EMPTY_DATA);
      setLoaded(false);
      setSuccess(result.message);
    } catch (err) {
      setError(err.message);
    }
  }, [userId]);

  const clear = useCallback(() => {
    setUserId('');
    setData(EMPTY_DATA);
    setLoaded(false);
    setError('');
    setSuccess('');
  }, []);

  const backToAdminMenu = useCallback(() => navigate('/admin'), [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3' || e.key === 'F12') {
        e.preventDefault();
        backToAdminMenu();
      } else if (e.key === 'F4') {
        e.preventDefault();
        clear();
      } else if (e.key === 'F5') {
        e.preventDefault();
        remove();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [backToAdminMenu, clear, remove]);

  return (
    <Layout tranId="CU03" progName="COUSR03C" title="Delete User" pfKeys={PF_KEYS}>
      <form style={styles.form} onSubmit={onEnter}>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="userId">Enter User ID:</label>
          <input id="userId" style={styles.input} maxLength={8}
                 value={userId} onChange={(e) => setUserId(e.target.value)} autoFocus />
          <span style={styles.hint}>(8 Char)</span>
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="firstName">First Name:</label>
          <input id="firstName" style={readOnly} value={data.firstName} readOnly />
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="lastName">Last Name:</label>
          <input id="lastName" style={readOnly} value={data.lastName} readOnly />
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="userType">User Type: </label>
          <input id="userType" style={{ ...readOnly, width: '40px' }} value={data.userType} readOnly />
          <span style={styles.hint}>(A=Admin, U=User)</span>
        </div>

        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Fetch</button>
          <button type="button" style={styles.button} onClick={backToAdminMenu}>F3 — Back</button>
          <button type="button" style={styles.button} onClick={clear}>F4 — Clear</button>
          <button type="button" style={styles.button} onClick={remove} disabled={!loaded}>F5 — Delete</button>
        </div>
      </form>

      <div style={styles.error} role="alert">{error}</div>
      <div style={styles.success} role="status">{success}</div>
    </Layout>
  );
}
