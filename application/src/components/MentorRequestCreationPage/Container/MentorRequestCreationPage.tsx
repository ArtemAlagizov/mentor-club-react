import React, { ChangeEvent, useEffect, useState } from "react";
import { connect } from "react-redux";
import MentorRequestCreationPageView from "../View/MentorRequestCreationPageView";
import {
  clearNewMentorRequestId,
  createMentorRequestBegin,
  fetchMentorListBegin
} from "../../../state/MentorRequests/actions";
import { IAppState } from "../../../store";
import {
  selectIsFetchingMentors,
  selectMentors,
  selectNewMentorRequestId
} from "../../../state/MentorRequests/selectors";
import { IMentorRequestCreationPageProps } from "./IMentorRequestCreationPageProps";
import { useHistory } from "react-router";
import { IUser } from "../../../state/MentorRequests/types";

export const MentorRequestCreationPage: React.FC<IMentorRequestCreationPageProps> = ({
  mentors,
  fetchMentorListBegin,
  createMentorRequestBegin,
  clearNewMentorRequestId,
  isFetchingMentorList,
  newMentorRequestId
}) => {
  const history = useHistory();
  const [selectedMentor, setSelectedMentor] = useState<IUser | null>(null);
  const [mentorRequestDescription, setMentorRequestDescription] = useState<
    string
  >("");

  useEffect(() => {
    fetchMentorListBegin();
  }, [fetchMentorListBegin]);

  useEffect(() => {
    if (newMentorRequestId) {
      history.push(`/mentor-requests/${newMentorRequestId}`);
    }
  }, [newMentorRequestId, history]);

  useEffect(() => {
    return () => {
      clearNewMentorRequestId();
    };
  }, [clearNewMentorRequestId]);

  const updateMentorRequestDescription = (
    event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => setMentorRequestDescription(event.target.value);

  const createMentorRequest = () => {
    createMentorRequestBegin({
      mentorRequestId: "",
      requestDescription: "",
      requesterName: "",
      status: "CREATED",
      requesterPictureThumbnailSrc: ""
    });
  };

  return (
    <MentorRequestCreationPageView
      mentors={mentors}
      selectedMentor={selectedMentor}
      setSelectedMentor={setSelectedMentor}
      mentorRequestDescription={mentorRequestDescription}
      updateMentorRequestDescription={updateMentorRequestDescription}
      isLoading={isFetchingMentorList}
      createMentorRequest={createMentorRequest}
    />
  );
};

export const mapStateToProps = (state: IAppState) => ({
  mentors: selectMentors(state),
  isFetchingMentorList: selectIsFetchingMentors(state),
  newMentorRequestId: selectNewMentorRequestId(state)
});

export const mapDispatchToProps = {
  fetchMentorListBegin,
  createMentorRequestBegin,
  clearNewMentorRequestId
} as const;

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(MentorRequestCreationPage);
