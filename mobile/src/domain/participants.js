// Memberships from an older shift may include management or archived profiles.
// The configuration endpoint accepts active employee memberships only.
export function activeShiftMemberIds(people, ids) {
  return [...new Set(ids.map(String))].filter((id) => {
    const person = people.find((row) => String(row.id) === id);
    return person?.status === "ACTIVE" && person.role !== "MANAGER"
      && (!person.backendRole || person.backendRole === "EMPLOYEE") && person.membershipId;
  });
}
