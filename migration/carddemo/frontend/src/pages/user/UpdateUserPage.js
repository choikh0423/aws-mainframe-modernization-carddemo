import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';
import { getUser, updateUser } from '../../api/users';

/**
 * CU02 — Update User (COUSR02C / map COUSR2A):
 *   - ENTER with a User ID -> fetch and paint the record, then
 *     "Press PF5 key to save your updates ..." (FR-UU-3, cbl:334-339)
 *   - an unknown id -> "User ID NOT found..." (FR-UU-4, cbl:340-346)
 *   - PF5 -> rewrite the four data fields; unchanged ->
 *     "Please modify to update ..." (FR-UU-8, cbl:238-243); changed ->
 *     "User <id> has been updated ..." (FR-UU-7, cbl:368-376)
 *   - PF3 -> save, then COADM01C (FR-UU-12, quirk Q4, cbl:129-132)
 *   - PF4 -> clear (FR-UU-13); PF12 -> cancel to COADM01C without saving
 *     (FR-UU-14, cbl:137-140)
 * Reached from CU00 with the selected id in the query string, exactly as the
 * legacy screen is reached with CDEMO-CU00-USR-SELECTED in the COMMAREA.
 */

const FETCH_PROMPT = 'Press PF5 key to save your updates ...';
const PF_KEYS = 'ENTER=Fetch  F3=Save&Exit  F4=Clear  F5=Save  F12=Cancel';

const EMPTY_DATA = { firstName: '', lastName: '', password: '', userType: '' };

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

export default function UpdateUserPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const selectedId = params.get('id') || '';

  const [userId, setUserId] = useState(selectedId);
  const [data, setData] = useState(EMPTY_DATA);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const set = (field) => (e) => setData((prev) => ({ ...prev, [field]: e.target.value }));

  const fetchUser = useCallback(async (id) => {
    setError('');
    setSuccess('');
    try {
      const record = await getUser(id);
      setData({
        firstName: record.firstName,
        lastName: record.lastName,
        password: record.password,
        userType: record.userType,
      });
      setLoaded(true);
      setSuccess(FETCH_PROMPT);
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

  const save = useCallback(async () => {
    setError('');
    setSuccess('');
    try {
      const result = await updateUser(userId, data);
      setSuccess(result.message);
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    }
  }, [userId, data]);

  // FR-UU-12 / quirk Q4: PF3 saves and leaves regardless of the save outcome.
  const saveAndExit = useCallback(async () => {
    await save();
    navigate('/admin');
  }, [save, navigate]);

  const clear = useCallback(() => {
    setUserId('');
    setData(EMPTY_DATA);
    setLoaded(false);
    setError('');
    setSuccess('');
  }, []);

  const cancel = useCallback(() => navigate('/admin'), [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        saveAndExit();
      } else if (e.key === 'F4') {
        e.preventDefault();
        clear();
      } else if (e.key === 'F5') {
        e.preventDefault();
        save();
      } else if (e.key === 'F12') {
        e.preventDefault();
        cancel();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [saveAndExit, clear, save, cancel]);

  return (
    <Layout tranId="CU02" progName="COUSR02C" title="Update User" pfKeys={PF_KEYS}>
      <form style={styles.form} onSubmit={onEnter}>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="userId">Enter User ID:</label>
          <input id="userId" style={styles.input} maxLength={8}
                 value={userId} onChange={(e) => setUserId(e.target.value)} autoFocus />
          <span style={styles.hint}>(8 Char)</span>
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="firstName">First Name:</label>
          <input id="firstName" style={styles.input} maxLength={20}
                 value={data.firstName} onChange={set('firstName')} />
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="lastName">Last Name:</label>
          <input id="lastName" style={styles.input} maxLength={20}
                 value={data.lastName} onChange={set('lastName')} />
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="password">Password:</label>
          {/* DRK (non-display) on the map. */}
          <input id="password" type="password" style={styles.input} maxLength={8}
                 value={data.password} onChange={set('password')} />
          <span style={styles.hint}>(8 Char)</span>
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="userType">User Type: </label>
          <input id="userType" style={{ ...styles.input, width: '40px' }} maxLength={1}
                 value={data.userType} onChange={set('userType')} />
          <span style={styles.hint}>(A=Admin, U=User)</span>
        </div>

        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Fetch</button>
          <button type="button" style={styles.button} onClick={saveAndExit}>F3 — Save&amp;Exit</button>
          <button type="button" style={styles.button} onClick={clear}>F4 — Clear</button>
          <button type="button" style={styles.button} onClick={save} disabled={!loaded}>F5 — Save</button>
          <button type="button" style={styles.button} onClick={cancel}>F12 — Cancel</button>
        </div>
      </form>

      <div style={styles.error} role="alert">{error}</div>
      <div style={styles.success} role="status">{success}</div>
    </Layout>
  );
}
